package stasis.client.tracking.trackers

import akka.NotUsed
import akka.actor.typed.scaladsl.LoggerOps
import akka.stream.scaladsl.Source
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.model.TargetEntity
import stasis.client.tracking.RecoveryTracker
import stasis.client.tracking.state.RecoveryState
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.events.EventLog
import stasis.core.streaming.Operators.ExtendedSource
import stasis.shared.ops.Operation

import java.nio.file.Path
import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class DefaultRecoveryTracker(
  maxRetention: FiniteDuration,
  backend: EventLogBackend[DefaultRecoveryTracker.RecoveryEvent, Map[Operation.Id, RecoveryState]]
) extends RecoveryTracker {
  import DefaultRecoveryTracker.RecoveryEvent

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val events: EventLog[RecoveryEvent, Map[Operation.Id, RecoveryState]] = EventLog(
    backend = backend,
    updateState = { case (event, state) => DefaultRecoveryTracker.updateState(event, state, maxRetention) }
  )

  override def state: Future[Map[Operation.Id, RecoveryState]] = events.state

  override def updates(operation: Operation.Id): Source[RecoveryState, NotUsed] =
    events.stateStream.dropLatestDuplicates(_.get(operation))

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

    val _ = events.store(event = RecoveryEvent.EntityExamined(operation, entity))
  }

  override def entityCollected(
    entity: TargetEntity
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (recovery) - Entity [{}] collected", operation, entity)

    val _ = events.store(event = RecoveryEvent.EntityCollected(operation, entity))
  }

  override def entityProcessingStarted(
    entity: Path,
    expectedParts: Int
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (recovery) - Entity [{}] processing started; expected parts: [{}]", operation, entity, expectedParts)

    val _ = events.store(event = RecoveryEvent.EntityProcessingStarted(operation, entity, expectedParts))
  }

  override def entityPartProcessed(
    entity: Path
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (recovery) - Part for entity [{}] processed", operation, entity)

    val _ = events.store(event = RecoveryEvent.EntityPartProcessed(operation, entity))
  }

  override def entityProcessed(
    entity: Path
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (recovery) - Entity [{}] processed", operation, entity)

    val _ = events.store(event = RecoveryEvent.EntityProcessed(operation, entity))
  }

  override def metadataApplied(
    entity: Path
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (recovery) - Metadata applied to entity [{}]", operation, entity)

    val _ = events.store(event = RecoveryEvent.EntityMetadataApplied(operation, entity))
  }

  override def failureEncountered(
    entity: Path,
    failure: Throwable
  )(implicit operation: Operation.Id): Unit = {
    log.debugN(
      "[{}] (recovery) - Failure encountered while processing entity [{}]: [{} - {}]",
      operation,
      entity,
      failure.getClass.getSimpleName,
      failure.getMessage
    )

    val _ = events.store(event = RecoveryEvent.EntityFailed(operation, entity, failure))
  }

  override def failureEncountered(
    failure: Throwable
  )(implicit operation: Operation.Id): Unit = {
    log.debugN(
      "[{}] (recovery) - Failure encountered: [{} - {}]",
      operation,
      failure.getClass.getSimpleName,
      failure.getMessage
    )

    val _ = events.store(event = RecoveryEvent.FailureEncountered(operation, failure))
  }

  override def completed()(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (recovery) - Operation completed", operation)

    val _ = events.store(event = RecoveryEvent.Completed(operation))
  }
}

object DefaultRecoveryTracker {
  def apply(
    maxRetention: FiniteDuration,
    createBackend: Map[Operation.Id, RecoveryState] => EventLogBackend[RecoveryEvent, Map[Operation.Id, RecoveryState]]
  ): DefaultRecoveryTracker =
    new DefaultRecoveryTracker(backend = createBackend(Map.empty), maxRetention = maxRetention)

  sealed trait RecoveryEvent {
    def operation: Operation.Id
  }

  object RecoveryEvent {
    final case class EntityExamined(
      override val operation: Operation.Id,
      entity: Path
    ) extends RecoveryEvent

    final case class EntityCollected(
      override val operation: Operation.Id,
      entity: TargetEntity
    ) extends RecoveryEvent

    final case class EntityProcessingStarted(
      override val operation: Operation.Id,
      entity: Path,
      expectedParts: Int
    ) extends RecoveryEvent

    final case class EntityPartProcessed(
      override val operation: Operation.Id,
      entity: Path
    ) extends RecoveryEvent

    final case class EntityProcessed(
      override val operation: Operation.Id,
      entity: Path
    ) extends RecoveryEvent

    final case class EntityMetadataApplied(
      override val operation: Operation.Id,
      entity: Path
    ) extends RecoveryEvent

    final case class EntityFailed(
      override val operation: Operation.Id,
      entity: Path,
      reason: Throwable
    ) extends RecoveryEvent

    final case class FailureEncountered(
      override val operation: Operation.Id,
      reason: Throwable
    ) extends RecoveryEvent

    final case class Completed(
      override val operation: Operation.Id
    ) extends RecoveryEvent
  }

  def updateState(
    event: RecoveryEvent,
    state: Map[Operation.Id, RecoveryState],
    maxRetention: FiniteDuration
  ): Map[Operation.Id, RecoveryState] = {
    import RecoveryEvent._

    val existing = state.getOrElse(event.operation, RecoveryState.start(event.operation))

    val updated = event match {
      case EntityExamined(_, entity)                         => existing.entityExamined(entity)
      case EntityCollected(_, entity)                        => existing.entityCollected(entity)
      case EntityProcessingStarted(_, entity, expectedParts) => existing.entityProcessingStarted(entity, expectedParts)
      case EntityPartProcessed(_, entity)                    => existing.entityPartProcessed(entity)
      case EntityProcessed(_, entity)                        => existing.entityProcessed(entity)
      case EntityMetadataApplied(_, entity)                  => existing.entityMetadataApplied(entity)
      case EntityFailed(_, entity, reason)                   => existing.entityFailed(entity, reason)
      case FailureEncountered(_, e)                          => existing.failureEncountered(e)
      case _: Completed                                      => existing.recoveryCompleted()
      case _                                                 => existing
    }

    val now = Instant.now()
    val filtered = state.filter(_._2.started.plusMillis(maxRetention.toMillis).isAfter(now))

    filtered + (event.operation -> updated)
  }
}
