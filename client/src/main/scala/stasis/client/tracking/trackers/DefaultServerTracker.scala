package stasis.client.tracking.trackers

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.stream.scaladsl.Source
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.client.tracking.ServerTracker
import stasis.client.tracking.ServerTracker.ServerState
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.events.EventLog
import stasis.layers.streaming.Operators.ExtendedSource

class DefaultServerTracker(
  backend: EventLogBackend[DefaultServerTracker.ServerEvent, Map[String, ServerState]]
) extends ServerTracker {
  import DefaultServerTracker.ServerEvent._
  import DefaultServerTracker._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val events: EventLog[ServerEvent, Map[String, ServerState]] = EventLog(
    backend = backend,
    updateState = { case (event, state) =>
      val updated = event match {
        case ServerReachable(_)   => ServerState(reachable = true, timestamp = Instant.now())
        case ServerUnreachable(_) => ServerState(reachable = false, timestamp = Instant.now())
      }

      state + (event.server -> updated)
    }
  )

  override def state: Future[Map[String, ServerState]] = events.state

  override def updates(server: String): Source[ServerState, NotUsed] = {
    val latest = Source.future(events.state).map(_.get(server)).collect { case Some(state) => state }
    latest.concat(events.stateStream.dropLatestDuplicates(_.get(server)))
  }

  override def reachable(server: String): Unit = {
    log.debugN("[{}] - Server reachable", server)
    val _ = events.store(event = ServerReachable(server = server))
  }

  override def unreachable(server: String): Unit = {
    log.debugN("[{}] - Server unreachable", server)
    val _ = events.store(event = ServerUnreachable(server = server))
  }
}

object DefaultServerTracker {
  def apply(
    createBackend: Map[String, ServerState] => EventLogBackend[ServerEvent, Map[String, ServerState]]
  ): DefaultServerTracker =
    new DefaultServerTracker(backend = createBackend(Map.empty))

  sealed trait ServerEvent {
    def server: String
  }

  object ServerEvent {
    final case class ServerReachable(override val server: String) extends ServerEvent
    final case class ServerUnreachable(override val server: String) extends ServerEvent
  }
}
