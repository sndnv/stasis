package stasis.client.tracking.state

import stasis.client.model.{EntityMetadata, SourceEntity}
import stasis.shared.ops.Operation

import java.nio.file.Path
import java.time.Instant

final case class BackupState(
  operation: Operation.Id,
  entities: BackupState.Entities,
  metadataCollected: Option[Instant],
  metadataPushed: Option[Instant],
  failures: Seq[String],
  completed: Option[Instant]
) extends OperationState {
  override val `type`: Operation.Type = Operation.Type.Backup

  override val isCompleted: Boolean = completed.isDefined

  def entityDiscovered(entity: Path): BackupState =
    copy(entities = entities.copy(discovered = entities.discovered + entity))

  def specificationProcessed(unmatched: Seq[String]): BackupState =
    copy(entities = entities.copy(unmatched = unmatched))

  def entityExamined(entity: Path): BackupState =
    copy(entities = entities.copy(examined = entities.examined + entity))

  def entityCollected(entity: SourceEntity): BackupState =
    copy(entities = entities.copy(collected = entities.collected + (entity.path -> entity)))

  def entityProcessingStarted(entity: Path, expectedParts: Int): BackupState =
    copy(
      entities = entities.copy(pending =
        entities.pending + (entity -> BackupState.PendingSourceEntity(expectedParts = expectedParts, processedParts = 0))
      )
    )

  def entityPartProcessed(entity: Path): BackupState =
    copy(entities = entities.copy(pending = entities.pending + (entity -> entities.pending(entity).inc())))

  def entityProcessed(entity: Path, metadata: Either[EntityMetadata, EntityMetadata]): BackupState = {
    val processed = entities.pending.get(entity) match {
      case Some(pending) =>
        BackupState.ProcessedSourceEntity(
          expectedParts = pending.expectedParts,
          processedParts = pending.processedParts,
          metadata = metadata
        )

      case None =>
        BackupState.ProcessedSourceEntity(
          expectedParts = 0,
          processedParts = 0,
          metadata = metadata
        )
    }

    copy(
      entities = entities.copy(
        pending = entities.pending - entity,
        processed = entities.processed + (entity -> processed)
      )
    )
  }

  def entityFailed(entity: Path, reason: Throwable): BackupState =
    copy(
      entities = entities.copy(
        failed = entities.failed + (entity -> s"${reason.getClass.getSimpleName} - ${reason.getMessage}")
      )
    )

  def backupMetadataCollected(): BackupState =
    copy(metadataCollected = Some(Instant.now()))

  def backupMetadataPushed(): BackupState =
    copy(metadataPushed = Some(Instant.now()))

  def failureEncountered(failure: Throwable): BackupState =
    copy(failures = failures :+ s"${failure.getClass.getSimpleName} - ${failure.getMessage}")

  def backupCompleted(): BackupState =
    copy(completed = Some(Instant.now()))

  def remainingEntities(): Seq[SourceEntity] =
    completed match {
      case Some(_) => Seq.empty
      case None    => entities.collected.view.filterKeys(entity => !entities.processed.contains(entity)).values.toSeq
    }

  override def asProgress: Operation.Progress = Operation.Progress(
    total = entities.discovered.size,
    processed = entities.processed.size,
    failures = entities.failed.size + failures.size,
    completed = completed
  )
}

object BackupState {
  def start(operation: Operation.Id): BackupState = BackupState(
    operation = operation,
    entities = Entities.empty,
    metadataCollected = None,
    metadataPushed = None,
    failures = Seq.empty,
    completed = None
  )

  final case class Entities(
    discovered: Set[Path],
    unmatched: Seq[String],
    examined: Set[Path],
    collected: Map[Path, SourceEntity],
    pending: Map[Path, PendingSourceEntity],
    processed: Map[Path, ProcessedSourceEntity],
    failed: Map[Path, String]
  )

  object Entities {
    def empty: Entities = Entities(
      discovered = Set.empty,
      unmatched = Seq.empty,
      examined = Set.empty,
      collected = Map.empty,
      pending = Map.empty,
      processed = Map.empty,
      failed = Map.empty
    )
  }

  final case class PendingSourceEntity(
    expectedParts: Int,
    processedParts: Int
  ) {
    def inc(): PendingSourceEntity =
      copy(processedParts = processedParts + 1)

    def toProcessed(withMetadata: Either[EntityMetadata, EntityMetadata]): ProcessedSourceEntity =
      ProcessedSourceEntity(
        expectedParts = expectedParts,
        processedParts = processedParts,
        metadata = withMetadata
      )
  }

  final case class ProcessedSourceEntity(
    expectedParts: Int,
    processedParts: Int,
    metadata: Either[EntityMetadata, EntityMetadata]
  )
}
