package stasis.client.tracking.trackers

import akka.NotUsed
import akka.actor.typed.scaladsl.LoggerOps
import akka.stream.scaladsl.Source
import org.slf4j.{Logger, LoggerFactory}
import stasis.client.collection.rules.Rule
import stasis.client.model.{EntityMetadata, SourceEntity}
import stasis.client.tracking.BackupTracker
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.trackers.DefaultBackupTracker.updateState
import stasis.core.persistence.backends.EventLogBackend
import stasis.core.persistence.events.EventLog
import stasis.core.streaming.Operators.ExtendedSource
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.ops.Operation

import java.nio.file.Path
import scala.concurrent.Future

class DefaultBackupTracker(
  backend: EventLogBackend[DefaultBackupTracker.BackupEvent, Map[Operation.Id, BackupState]]
) extends BackupTracker {
  import DefaultBackupTracker.BackupEvent

  private val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val events: EventLog[BackupEvent, Map[Operation.Id, BackupState]] = EventLog(
    backend = backend,
    updateState = { case (event, state) => updateState(event, state) }
  )

  override def state: Future[Map[Operation.Id, BackupState]] = events.state

  override def updates(operation: Operation.Id): Source[BackupState, NotUsed] =
    events.stateStream.dropLatestDuplicates(_.get(operation))

  override def entityDiscovered(
    entity: Path
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Entity [{}] discovered", operation, entity)

    val _ = events.store(event = BackupEvent.EntityDiscovered(operation, entity))
  }

  override def specificationProcessed(
    unmatched: Seq[(Rule, Throwable)]
  )(implicit operation: Operation.Id): Unit =
    if (unmatched.isEmpty) {
      log.debugN("[{}] (backup) - Specification successfully processed", operation)
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

      val _ = events.store(event = BackupEvent.SpecificationProcessed(operation, failures))
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

    val _ = events.store(event = BackupEvent.EntityExamined(operation, entity))
  }

  override def entityCollected(
    entity: SourceEntity
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Entity [{}] collected", operation, entity.path)

    val _ = events.store(event = BackupEvent.EntityCollected(operation, entity))
  }

  override def entityProcessingStarted(
    entity: Path,
    expectedParts: Int
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Entity [{}] processing started; expected parts: [{}]", operation, entity, expectedParts)

    val _ = events.store(event = BackupEvent.EntityProcessingStarted(operation, entity, expectedParts))
  }

  override def entityPartProcessed(
    entity: Path
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Part for entity [{}] processed", operation, entity)

    val _ = events.store(event = BackupEvent.EntityPartProcessed(operation, entity))
  }

  override def entityProcessed(
    entity: Path,
    metadata: Either[EntityMetadata, EntityMetadata]
  )(implicit operation: Operation.Id): Unit = {
    val contentChanged = metadata.isLeft
    log.debugN("[{}] (backup) - Entity [{}] processed; content changed: [{}]", operation, entity, contentChanged)

    val _ = events.store(event = BackupEvent.EntityProcessed(operation, entity, metadata))
  }

  override def metadataCollected()(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Metadata collected", operation)

    val _ = events.store(event = BackupEvent.MetadataCollected(operation))
  }

  override def metadataPushed(
    entry: DatasetEntry.Id
  )(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Metadata pushed; entry [{}] created", operation, entry)

    val _ = events.store(event = BackupEvent.MetadataPushed(operation))
  }

  override def failureEncountered(
    failure: Throwable
  )(implicit operation: Operation.Id): Unit = {
    log.debugN(
      "[{}] (backup) - Failure encountered: [{} - {}]",
      operation,
      failure.getClass.getSimpleName,
      failure.getMessage
    )

    val _ = events.store(event = BackupEvent.FailureEncountered(operation, failure))
  }

  override def failureEncountered(
    entity: Path,
    failure: Throwable
  )(implicit operation: Operation.Id): Unit = {
    log.debugN(
      "[{}] (backup) - Failure encountered while processing entity [{}]: [{} - {}]",
      operation,
      entity,
      failure.getClass.getSimpleName,
      failure.getMessage
    )

    val _ = events.store(event = BackupEvent.EntityFailed(operation, entity, failure))
  }

  override def completed()(implicit operation: Operation.Id): Unit = {
    log.debugN("[{}] (backup) - Operation completed", operation)

    val _ = events.store(event = BackupEvent.Completed(operation))
  }
}

object DefaultBackupTracker {
  def apply(
    createBackend: Map[Operation.Id, BackupState] => EventLogBackend[BackupEvent, Map[Operation.Id, BackupState]]
  ): DefaultBackupTracker =
    new DefaultBackupTracker(backend = createBackend(Map.empty))

  sealed trait BackupEvent {
    def operation: Operation.Id
  }

  object BackupEvent {
    final case class EntityDiscovered(
      override val operation: Operation.Id,
      entity: Path
    ) extends BackupEvent

    final case class SpecificationProcessed(
      override val operation: Operation.Id,
      unmatched: Seq[String]
    ) extends BackupEvent

    final case class EntityExamined(
      override val operation: Operation.Id,
      entity: Path
    ) extends BackupEvent

    final case class EntityCollected(
      override val operation: Operation.Id,
      entity: SourceEntity
    ) extends BackupEvent

    final case class EntityProcessingStarted(
      override val operation: Operation.Id,
      entity: Path,
      expectedParts: Int
    ) extends BackupEvent

    final case class EntityPartProcessed(
      override val operation: Operation.Id,
      entity: Path
    ) extends BackupEvent

    final case class EntityProcessed(
      override val operation: Operation.Id,
      entity: Path,
      metadata: Either[EntityMetadata, EntityMetadata]
    ) extends BackupEvent

    final case class EntityFailed(
      override val operation: Operation.Id,
      entity: Path,
      reason: Throwable
    ) extends BackupEvent

    final case class FailureEncountered(
      override val operation: Operation.Id,
      reason: Throwable
    ) extends BackupEvent

    final case class MetadataCollected(
      override val operation: Operation.Id
    ) extends BackupEvent

    final case class MetadataPushed(
      override val operation: Operation.Id
    ) extends BackupEvent

    final case class Completed(
      override val operation: Operation.Id
    ) extends BackupEvent
  }

  def updateState(
    event: BackupEvent,
    state: Map[Operation.Id, BackupState]
  ): Map[Operation.Id, BackupState] = {
    import BackupEvent._

    val existing = state.getOrElse(event.operation, BackupState.start(event.operation))

    val updated = event match {
      case EntityDiscovered(_, entity)                       => existing.entityDiscovered(entity)
      case SpecificationProcessed(_, unmatched)              => existing.specificationProcessed(unmatched)
      case EntityExamined(_, entity)                         => existing.entityExamined(entity)
      case EntityCollected(_, entity)                        => existing.entityCollected(entity)
      case EntityProcessingStarted(_, entity, expectedParts) => existing.entityProcessingStarted(entity, expectedParts)
      case EntityPartProcessed(_, entity)                    => existing.entityPartProcessed(entity)
      case EntityProcessed(_, entity, metadata)              => existing.entityProcessed(entity, metadata)
      case EntityFailed(_, entity, reason)                   => existing.entityFailed(entity, reason)
      case FailureEncountered(_, e)                          => existing.failureEncountered(e)
      case _: MetadataCollected                              => existing.backupMetadataCollected()
      case _: MetadataPushed                                 => existing.backupMetadataPushed()
      case _: Completed                                      => existing.backupCompleted()
      case _                                                 => existing
    }

    state + (event.operation -> updated)
  }
}
