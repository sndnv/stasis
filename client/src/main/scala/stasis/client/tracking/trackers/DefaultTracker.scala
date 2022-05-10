package stasis.client.tracking.trackers

import java.nio.file.Path
import akka.NotUsed
import akka.actor.typed.scaladsl.LoggerOps
import akka.stream.scaladsl.Source
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.collection.rules.Rule
import stasis.client.collection.rules.exceptions.RuleMatchingFailure
import stasis.client.tracking.{BackupTracker, RecoveryTracker, ServerTracker, TrackerView}
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.events.EventLog
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation

import scala.concurrent.Future

class DefaultTracker private (
  backend: EventLogBackend[DefaultTracker.Event, TrackerView.State]
) extends TrackerView {
  import DefaultTracker._
  import TrackerView._

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

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

  override def stateUpdates: Source[State, NotUsed] = events.stateStream

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  override def operationUpdates(operation: Operation.Id): Source[Operation.Progress, NotUsed] =
    stateUpdates
      .statefulMapConcat { () =>
        var last: Operation.Progress = Operation.Progress.empty

        { state =>
          state.operations
            .get(operation)
            .flatMap {
              case current if current == last =>
                None

              case current =>
                last = current
                Some(current)
            }
            .toList
        }
      }

  object server extends ServerTracker {
    override def reachable(server: String): Unit = {
      val _ = events.store(event = Event.ServerReachable(server = server))
    }

    override def unreachable(server: String): Unit = {
      val _ = events.store(event = Event.ServerUnreachable(server = server))
    }
  }

  object backup extends BackupTracker {
    override def specificationProcessed(
      unmatched: Seq[(Rule, Throwable)]
    )(implicit operation: Operation.Id): Unit =
      if (unmatched.isEmpty) {
        log.debugN("[{}] (backup) - Specification successfully processed", operation)

        val _ = events.store(
          event = Event.OperationStepCompleted(
            operationId = operation,
            stage = "specification",
            step = "processing"
          )
        )
      } else {
        val failures = unmatched.map { case (rule, e) =>
          s"Rule [${rule.asString}] failed with [${e.getClass.getSimpleName} - ${e.getMessage}]"
        }

        log.debugN(
          "[{}] (backup) - Specification processed with [{}] unmatched rules: [{}]",
          operation,
          failures.length,
          failures.mkString("\n\t", "\n\t", "\n")
        )

        failures.foreach { failure =>
          val _ = events.store(
            event = Event.OperationStepFailed(
              operationId = operation,
              failure = new RuleMatchingFailure(
                message = failure
              )
            )
          )
        }
      }

    override def entityExamined(
      entity: Path,
      metadataChanged: Boolean,
      contentChanged: Boolean
    )(implicit operation: Operation.Id): Unit = {
      log.debugN(
        "[{}] (backup) - Entity [{}] examined; metadata changed: [{}]; content changed: [{}]",
        operation,
        entity,
        metadataChanged,
        contentChanged
      )

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "examination",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def entityCollected(entity: Path)(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (backup) - Entity [{}] collected", operation, entity)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "collection",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def entityProcessed(entity: Path, contentChanged: Boolean)(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (backup) - Entity [{}] processed; content changed: [{}]", operation, entity, contentChanged)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "processing",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def metadataCollected()(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (backup) - Metadata collected", operation)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "metadata",
          step = "collection"
        )
      )
    }

    override def metadataPushed(entry: DatasetEntry.Id)(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (backup) - Metadata pushed; entry [{}] created", operation, entry)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "metadata",
          step = "push"
        )
      )
    }

    override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit = {
      log.debugN(
        "[{}] (backup) - Failure encountered: [{} - {}]",
        operation,
        failure.getClass.getSimpleName,
        failure.getMessage
      )

      val _ = events.store(
        event = Event.OperationStepFailed(
          operationId = operation,
          failure = failure
        )
      )
    }

    override def completed()(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (backup) - Operation completed", operation)

      val _ = events.store(
        event = Event.OperationCompleted(operationId = operation)
      )
    }
  }

  object recovery extends RecoveryTracker {
    override def entityExamined(
      entity: Path,
      metadataChanged: Boolean,
      contentChanged: Boolean
    )(implicit operation: Operation.Id): Unit = {
      log.debugN(
        "[{}] (recovery) - Entity [{}] examined; metadata changed: [{}]; content changed: [{}]",
        operation,
        entity,
        metadataChanged,
        contentChanged
      )

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "examination",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def entityCollected(entity: Path)(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (recovery) - Entity [{}] collected", operation, entity)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "collection",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def entityProcessed(entity: Path)(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (recovery) - Entity [{}] processed", operation, entity)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "processing",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def metadataApplied(entity: Path)(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (recovery) - Metadata applied to entity [{}]", operation, entity)

      val _ = events.store(
        event = Event.OperationStepCompleted(
          operationId = operation,
          stage = "metadata-applied",
          step = entity.toAbsolutePath.toString
        )
      )
    }

    override def failureEncountered(failure: Throwable)(implicit operation: Operation.Id): Unit = {
      log.debugN(
        "[{}] (recovery) - Failure encountered: [{} - {}]",
        operation,
        failure.getClass.getSimpleName,
        failure.getMessage
      )

      val _ = events.store(
        event = Event.OperationStepFailed(
          operationId = operation,
          failure = failure
        )
      )
    }

    override def completed()(implicit operation: Operation.Id): Unit = {
      log.debugN("[{}] (recovery) - Operation completed", operation)

      val _ = events.store(
        event = Event.OperationCompleted(operationId = operation)
      )
    }
  }
}

object DefaultTracker {
  def apply(
    createBackend: TrackerView.State => EventLogBackend[DefaultTracker.Event, TrackerView.State]
  ): DefaultTracker =
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
