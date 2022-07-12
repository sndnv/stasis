package stasis.client.tracking.state

import stasis.client.model.TargetEntity
import stasis.shared.ops.Operation

import java.nio.file.Path
import java.time.Instant

final case class RecoveryState(
  operation: Operation.Id,
  entities: RecoveryState.Entities,
  failures: Seq[String],
  completed: Option[Instant]
) extends OperationState {
  override val `type`: Operation.Type = Operation.Type.Recovery

  override val isCompleted: Boolean = completed.isDefined

  def entityExamined(entity: Path): RecoveryState =
    copy(entities = entities.copy(examined = entities.examined + entity))

  def entityCollected(entity: TargetEntity): RecoveryState =
    copy(entities = entities.copy(collected = entities.collected + (entity.path -> entity)))

  def entityProcessingStarted(entity: Path, expectedParts: Int): RecoveryState =
    copy(
      entities = entities.copy(
        pending =
          entities.pending + (entity -> RecoveryState.PendingTargetEntity(expectedParts = expectedParts, processedParts = 0))
      )
    )

  def entityPartProcessed(entity: Path): RecoveryState =
    copy(entities = entities.copy(pending = entities.pending + (entity -> entities.pending(entity).inc())))

  def entityProcessed(entity: Path): RecoveryState = {
    val processed = entities.pending.get(entity) match {
      case Some(pending) =>
        RecoveryState.ProcessedTargetEntity(
          expectedParts = pending.expectedParts,
          processedParts = pending.processedParts
        )

      case None =>
        RecoveryState.ProcessedTargetEntity(
          expectedParts = 0,
          processedParts = 0
        )
    }

    copy(
      entities = entities.copy(
        pending = entities.pending - entity,
        processed = entities.processed + (entity -> processed)
      )
    )
  }

  def entityMetadataApplied(entity: Path): RecoveryState =
    copy(entities = entities.copy(metadataApplied = entities.metadataApplied + entity))

  def entityFailed(entity: Path, reason: Throwable): RecoveryState =
    copy(entities =
      entities.copy(failed = entities.failed + (entity -> s"${reason.getClass.getSimpleName} - ${reason.getMessage}"))
    )

  def failureEncountered(failure: Throwable): RecoveryState =
    copy(failures = failures :+ s"${failure.getClass.getSimpleName} - ${failure.getMessage}")

  def recoveryCompleted(): RecoveryState =
    copy(completed = Some(Instant.now()))

  override def asProgress: Operation.Progress = Operation.Progress(
    total = entities.examined.size,
    processed = entities.processed.size,
    failures = entities.failed.size + failures.size,
    completed = completed
  )
}

object RecoveryState {
  def start(operation: Operation.Id): RecoveryState =
    RecoveryState(
      operation = operation,
      entities = Entities.empty,
      failures = Seq.empty,
      completed = None
    )

  final case class Entities(
    examined: Set[Path],
    collected: Map[Path, TargetEntity],
    pending: Map[Path, PendingTargetEntity],
    processed: Map[Path, ProcessedTargetEntity],
    metadataApplied: Set[Path],
    failed: Map[Path, String]
  )

  object Entities {
    def empty: Entities = Entities(
      examined = Set.empty,
      collected = Map.empty,
      pending = Map.empty,
      processed = Map.empty,
      metadataApplied = Set.empty,
      failed = Map.empty
    )
  }

  final case class PendingTargetEntity(
    expectedParts: Int,
    processedParts: Int
  ) {
    def inc(): PendingTargetEntity =
      copy(processedParts = processedParts + 1)
  }

  final case class ProcessedTargetEntity(
    expectedParts: Int,
    processedParts: Int
  )
}
