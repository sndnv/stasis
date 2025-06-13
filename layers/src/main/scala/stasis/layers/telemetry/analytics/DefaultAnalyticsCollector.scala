package stasis.layers.telemetry.analytics

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.Scheduler
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.TimerScheduler
import org.apache.pekko.util.Timeout

import stasis.layers.telemetry.ApplicationInformation

class DefaultAnalyticsCollector private (
  storeRef: ActorRef[DefaultAnalyticsCollector.Message],
  override val persistence: Option[AnalyticsPersistence]
)(implicit scheduler: Scheduler, timeout: Timeout)
    extends AnalyticsCollector {
  override def recordEvent(name: String, attributes: Map[String, String]): Unit =
    storeRef ! DefaultAnalyticsCollector.RecordEvent(name, attributes = attributes)

  override def recordFailure(message: String): Unit =
    storeRef ! DefaultAnalyticsCollector.RecordFailure(message = message)

  override def state: Future[AnalyticsEntry] =
    storeRef ? ((ref: ActorRef[AnalyticsEntry]) => DefaultAnalyticsCollector.GetState(ref))

  override def send(): Unit =
    storeRef ! DefaultAnalyticsCollector.Send

  def stop(): Unit =
    storeRef ! DefaultAnalyticsCollector.Stop
}

object DefaultAnalyticsCollector {
  def apply(
    name: String,
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation
  )(implicit system: ActorSystem[Nothing], timeout: Timeout): DefaultAnalyticsCollector = {
    val storeRef = system.systemActorOf(
      behavior = restoring()(config, persistence, app),
      name = s"$name-${java.util.UUID.randomUUID().toString}"
    )

    storeRef ! LoadState

    new DefaultAnalyticsCollector(storeRef = storeRef, persistence = Some(persistence))
  }

  final case class Config(
    persistenceInterval: FiniteDuration,
    transmissionInterval: FiniteDuration
  )

  private def restoring()(implicit
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation
  ): Behavior[Message] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.withTimers { implicit scheduler =>
        Behaviors.receive {
          case (ctx, LoadState) =>
            ctx.pipeToSelf(persistence.restore()) {
              case Success(entry) =>
                val actual = entry.map(_.asCollected()).getOrElse(AnalyticsEntry.collected(app))

                ctx.log.debug(
                  "Analytics state successfully loaded with [events={},failures={}]",
                  actual.events.length,
                  actual.failures.length
                )

                StateLoaded(entry = actual)

              case Failure(e) =>
                ctx.log.error(
                  "Failed to load analytics state: [{} - {}]",
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                StateLoaded(entry = AnalyticsEntry.collected(app))
            }
            Behaviors.same

          case (_, StateLoaded(entry)) =>
            buffer.unstashAll(collecting(entry))

          case (_, other) =>
            val _ = buffer.stash(other)
            Behaviors.same
        }
      }
    }

  private def collecting(
    entry: AnalyticsEntry.Collected
  )(implicit
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation,
    scheduler: TimerScheduler[Message]
  ): Behavior[Message] =
    Behaviors
      .receivePartial[Message] {
        case (_, RecordEvent(name, attributes)) =>
          if (!scheduler.isTimerActive(PersistStateTimerKey)) {
            scheduler.startSingleTimer(PersistStateTimerKey, PersistState(forceTransmit = false), config.persistenceInterval)
          }

          collecting(entry = entry.withEvent(name = name, attributes = attributes))

        case (ctx, RecordFailure(message)) =>
          scheduler.cancel(PersistStateTimerKey)
          ctx.self ! PersistState(forceTransmit = false)

          collecting(entry = entry.withFailure(message = message))

        case (ctx, PersistState(forceTransmit)) =>
          if (
            forceTransmit || persistence.lastTransmitted.plusMillis(config.transmissionInterval.toMillis).isBefore(Instant.now())
          ) {
            ctx.pipeToSelf(persistence.transmit(entry)) {
              case Success(_) =>
                ctx.log.debug(
                  "Analytics state successfully transmitted with [events={},failures={}]",
                  entry.events.length,
                  entry.failures.length
                )

                StateTransmitted(successful = true)

              case Failure(e) =>
                ctx.log.error(
                  "Failed to transmit analytics state with [events={},failures={}]: [{} - {}]",
                  entry.events.length,
                  entry.failures.length,
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                StateTransmitted(successful = false)
            }
            transmitting(pending = entry)
          } else {
            persistence.cache(entry = entry)
            Behaviors.same
          }

        case (_, GetState(replyTo)) =>
          replyTo.tell(entry)
          Behaviors.same

        case (ctx, Send) =>
          ctx.self ! PersistState(forceTransmit = true)
          Behaviors.same

        case (_, Stop) =>
          Behaviors.stopped
      }
      .receiveSignal { case (_, PostStop) =>
        scheduler.cancel(PersistStateTimerKey)
        persistence.cache(entry = entry)
        Behaviors.same
      }

  private def transmitting(
    pending: AnalyticsEntry.Collected
  )(implicit
    config: Config,
    persistence: AnalyticsPersistence,
    app: ApplicationInformation,
    scheduler: TimerScheduler[Message]
  ): Behavior[Message] =
    Behaviors.withStash(capacity = Int.MaxValue) { buffer =>
      Behaviors.receiveMessage {
        case StateTransmitted(true) =>
          val empty = AnalyticsEntry.collected(app)
          persistence.cache(entry = empty)
          buffer.unstashAll(collecting(entry = empty))

        case StateTransmitted(false) =>
          persistence.cache(entry = pending)
          buffer.unstashAll(collecting(entry = pending))

        case other =>
          val _ = buffer.stash(other)
          Behaviors.same
      }
    }

  private sealed trait Message
  private final case class RecordEvent(name: String, attributes: Map[String, String]) extends Message
  private final case class RecordFailure(message: String) extends Message
  private final case class GetState(replyTo: ActorRef[AnalyticsEntry]) extends Message
  private final case object Send extends Message
  private final case class PersistState(forceTransmit: Boolean) extends Message
  private final case object LoadState extends Message
  private final case class StateLoaded(entry: AnalyticsEntry.Collected) extends Message
  private final case class StateTransmitted(successful: Boolean) extends Message
  private final case object Stop extends Message

  private object PersistStateTimerKey
}
