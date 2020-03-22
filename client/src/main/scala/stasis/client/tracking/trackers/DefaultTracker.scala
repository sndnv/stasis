package stasis.client.tracking.trackers

import java.nio.file.Path

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.event.{Logging, LoggingAdapter}
import stasis.client.tracking.{BackupTracker, RecoveryTracker, ServerTracker, TrackerView}
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.events.EventLog
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation

import scala.concurrent.Future

class DefaultTracker private (
  backend: EventLogBackend[DefaultTracker.Event, TrackerView.State]
)(implicit system: ActorSystem[SpawnProtocol])
    extends TrackerView {
  import DefaultTracker._
  import TrackerView._

  private val log: LoggingAdapter = Logging(system.toUntyped, this.getClass.getName)

  private val events: EventLog[Event, State] = EventLog(
    backend = backend,
    updateState = (event, state) =>
      event match {
        case Event.ServerReachable(server) =>
          state.withServer(
            server = server,
            reachable = true
          )

        case Event.ServerUnreachable(server) =>
          state.withServer(
            server = server,
            reachable = false
          )

        case Event.OperationStepCompleted(operationId, stage, step) =>
          state.withStep(
            operationId = operationId,
            stage = stage,
            step = step
          )

        case Event.OperationStepFailed(operationId, failure) =>
          state.withFailure(
            operationId = operationId,
            failure = failure
          )

        case Event.OperationCompleted(operationId) =>
          state.completed(operationId = operationId)
    }
  )

  override def state: Future[TrackerView.State] = events.state

  object server extends ServerTracker {
    override def reachable(server: String): Unit = {
      val _ = events.store(event = Event.ServerReachable(server = server))
    }

    override def unreachable(server: String): Unit = {
      val _ = events.store(event = Event.ServerUnreachable(server = server))
    }
  }

  object backup extends BackupTracker {
    override def fileExamined(
      file: Path,
      metadataChanged: Boolean,
      contentChanged: Boolean
    )(implicit operation: Operation.Id): Unit = {
      log.debug(
        "[{}] (backup) - File [{}] examined; metadata changed: [{}]; content changed: [{}]",
        operation,
        file,
        metadataChanged,
        contentChanged
      )

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "examination",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def fileCollected(file: Path)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (backup) - File [{}] collected", operation, file)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "collection",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def fileProcessed(file: Path, contentChanged: Boolean)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (backup) - File [{}] processed; content changed: [{}]", operation, file, contentChanged)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "processing",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def metadataCollected()(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (backup) - Metadata collected", operation)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "metadata",
          step = "collection"
        )
      )
    }

    override def metadataPushed(entry: DatasetEntry.Id)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (backup) - Metadata pushed; entry [{}] created", operation, entry)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "metadata",
          step = "push"
        )
      )
    }

    override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (backup) - Failure encountered: [{}]", operation, failure.getMessage)

      val _ = events.store(
        event = Event.OperationStepFailed(
          operationId = operation,
          failure = failure
        )
      )
    }

    override def completed()(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (backup) - Operation completed", operation)

      val _ = events.store(
        event = Event.OperationCompleted(operationId = operation)
      )
    }
  }

  object recovery extends RecoveryTracker {
    override def fileExamined(
      file: Path,
      metadataChanged: Boolean,
      contentChanged: Boolean
    )(implicit operation: Operation.Id): Unit = {
      log.debug(
        "[{}] (recovery) - File [{}] examined; metadata changed: [{}]; content changed: [{}]",
        operation,
        file,
        metadataChanged,
        contentChanged
      )

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "examination",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def fileCollected(file: Path)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (recovery) - File [{}] collected", operation, file)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "collection",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def fileProcessed(file: Path)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (recovery) - File [{}] processed", operation, file)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "processing",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def metadataApplied(file: Path)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (recovery) - Metadata applied to file [{}]", operation, file)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "metadata-applied",
          step = file.toAbsolutePath.toString
        )
      )
    }

    override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (recovery) - Failure encountered: [{}]", operation, failure.getMessage)

      val _ = events.store(
        event = Event.OperationStepFailed(
          operationId = operation,
          failure = failure
        )
      )
    }

    override def completed()(implicit operation: Operation.Id): Unit = {
      log.debug("[{}] (recovery) - Operation completed", operation)

      val _ = events.store(
        event = Event.OperationCompleted(operationId = operation)
      )
    }
  }
}

object DefaultTracker {
  def apply(
    createBackend: TrackerView.State => EventLogBackend[DefaultTracker.Event, TrackerView.State]
  )(implicit system: ActorSystem[SpawnProtocol]): DefaultTracker =
    new DefaultTracker(
      backend = createBackend(TrackerView.State.empty)
    )

  private sealed trait Event

  private object Event {
    final case class ServerReachable(server: String) extends Event

    final case class ServerUnreachable(server: String) extends Event

    final case class OperationStepCompleted(
      operationId: Operation.Id,
      stage: String,
      step: String
    ) extends Event

    final case class OperationStepFailed(
      operationId: Operation.Id,
      failure: Throwable
    ) extends Event

    final case class OperationCompleted(
      operationId: Operation.Id
    ) extends Event
  }
}
