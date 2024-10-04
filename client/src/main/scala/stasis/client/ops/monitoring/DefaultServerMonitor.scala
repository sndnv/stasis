package stasis.client.ops.monitoring

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.Done
import org.apache.pekko.actor.typed._
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.util.Timeout

import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.tracking.ServerTracker
import stasis.shared.api.responses.Ping

class DefaultServerMonitor private (
  monitorRef: ActorRef[DefaultServerMonitor.Message]
)(implicit scheduler: Scheduler, timeout: Timeout)
    extends ServerMonitor {
  override def stop(): Future[Done] =
    monitorRef ? (ref => DefaultServerMonitor.Stop(ref))
}

object DefaultServerMonitor {
  def apply(
    initialDelay: FiniteDuration,
    interval: FiniteDuration,
    api: ServerApiEndpointClient,
    tracker: ServerTracker
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultServerMonitor = {
    val behaviour = monitor(
      initialDelay = initialDelay,
      interval = interval,
      api = api,
      tracker = tracker
    )

    new DefaultServerMonitor(
      monitorRef = system.systemActorOf(behaviour, name = s"server-monitor-${java.util.UUID.randomUUID().toString}")
    )
  }

  private sealed trait Message
  private case object PingServer extends Message
  private final case class ScheduleNextPing(after: FiniteDuration) extends Message
  private final case class Stop(replyTo: ActorRef[Done]) extends Message

  private case object PingTimerKey

  private def monitor(
    initialDelay: FiniteDuration,
    interval: FiniteDuration,
    api: ServerApiEndpointClient,
    tracker: ServerTracker
  ): Behavior[Message] =
    Behaviors.withTimers[Message] { timers =>
      timers.startSingleTimer(PingTimerKey, PingServer, initialDelay)

      Behaviors.receive { case (ctx, message) =>
        message match {
          case PingServer =>
            val log = ctx.log
            val self = ctx.self

            api
              .ping()
              .onComplete {
                case Success(Ping(id)) =>
                  log.debugN("Server [{}] responded to ping with [{}]", api.server, id)
                  tracker.reachable(api.server)
                  self ! ScheduleNextPing(after = interval)

                case Failure(e) =>
                  log.errorN("Failed to reach server [{}]: [{} - {}]", api.server, e.getClass.getSimpleName, e.getMessage)
                  tracker.unreachable(api.server)
                  self ! ScheduleNextPing(after = interval / 2)
              }(ctx.executionContext)

            Behaviors.same

          case ScheduleNextPing(after) =>
            ctx.log.debugN(
              "Scheduling next ping for server [{}] in [{}] second(s)",
              api.server,
              after.toSeconds
            )
            timers.startSingleTimer(PingTimerKey, PingServer, after)
            Behaviors.same

          case Stop(replyTo) =>
            ctx.log.debugN("Stopping monitor for server [{}]", api.server)
            replyTo ! Done
            Behaviors.stopped
        }
      }
    }
}
