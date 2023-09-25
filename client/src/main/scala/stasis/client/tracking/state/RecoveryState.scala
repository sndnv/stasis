package stasis.client.tracking.state

import stasis.client.model.TargetEntity.Destination
import stasis.client.model.{proto, EntityMetadata, TargetEntity}
import stasis.shared.ops.Operation

import java.nio.file.{Path, Paths}
import java.time.Instant
import scala.util.{Failure, Success, Try}

final case class RecoveryState(
  operation: Operation.Id,
  started: Instant,
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
    started = started,
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
      started = Instant.now(),
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

  def toProto(state: RecoveryState): proto.state.RecoveryState =
    proto.state.RecoveryState(
      started = state.started.toEpochMilli,
      entities = Some(
        proto.state.RecoveryEntities(
          examined = state.entities.examined.map(_.toAbsolutePath.toString).toSeq,
          collected = state.entities.collected.map { case (k, v) =>
            k.toAbsolutePath.toString -> toProtoTargetEntity(v)
          },
          pending = state.entities.pending.map { case (k, v) =>
            k.toAbsolutePath.toString -> toProtoPendingTargetEntity(v)
          },
          processed = state.entities.processed.map { case (k, v) =>
            k.toAbsolutePath.toString -> toProtoProcessedTargetEntity(v)
          },
          metadataApplied = state.entities.metadataApplied.map(_.toAbsolutePath.toString).toSeq,
          failed = state.entities.failed.map { case (k, v) => k.toAbsolutePath.toString -> v }
        )
      ),
      failures = state.failures,
      completed = state.completed.map(_.toEpochMilli)
    )

  def fromProto(operation: Operation.Id, state: proto.state.RecoveryState): Try[RecoveryState] =
    state.entities match {
      case Some(entities) =>
        Try {
          RecoveryState(
            operation = operation,
            started = Instant.ofEpochMilli(state.started),
            entities = RecoveryState.Entities(
              examined = entities.examined.map(Paths.get(_)).toSet,
              collected = entities.collected.map { case (k, v) => Paths.get(k) -> fromProtoTargetEntity(v) },
              pending = entities.pending.map { case (k, v) => Paths.get(k) -> fromProtoPendingTargetEntity(v) },
              processed = entities.processed.map { case (k, v) => Paths.get(k) -> fromProtoProcessedTargetEntity(v) },
              metadataApplied = entities.metadataApplied.map(Paths.get(_)).toSet,
              failed = entities.failed.map { case (k, v) => Paths.get(k) -> v }
            ),
            failures = state.failures,
            completed = state.completed.map(Instant.ofEpochMilli)
          )
        }

      case None =>
        Failure(new IllegalArgumentException("Expected entities in recovery state but none were found"))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def fromProtoTargetEntity(entity: proto.state.TargetEntity): TargetEntity =
    TargetEntity(
      path = Paths.get(entity.path),
      destination = entity.destination match {
        case directory: proto.state.TargetEntityDestinationDirectory =>
          TargetEntity.Destination.Directory(
            path = Paths.get(directory.path),
            keepDefaultStructure = directory.keepDefaultStructure
          )

        case _ =>
          TargetEntity.Destination.Default
      },
      existingMetadata = entity.existingMetadata.flatMap(metadata => EntityMetadata.fromProto(metadata).toOption) match {
        case Some(metadata) => metadata
        case None => throw new IllegalArgumentException("Expected existing metadata in recovery state but none was found")
      },
      currentMetadata = entity.currentMetadata.map { metadata =>
        EntityMetadata.fromProto(metadata) match {
          case Success(metadata) => metadata
          case Failure(e)        => throw e
        }
      }
    )

  private def toProtoTargetEntity(entity: TargetEntity): proto.state.TargetEntity =
    proto.state.TargetEntity(
      path = entity.path.toAbsolutePath.toString,
      destination = entity.destination match {
        case directory: Destination.Directory =>
          proto.state.TargetEntityDestinationDirectory(
            path = directory.path.toAbsolutePath.toString,
            keepDefaultStructure = directory.keepDefaultStructure
          )

        case Destination.Default =>
          proto.state.TargetEntityDestinationDefault()
      },
      existingMetadata = Some(EntityMetadata.toProto(entity.existingMetadata)),
      currentMetadata = entity.currentMetadata.map(EntityMetadata.toProto)
    )

  private def fromProtoPendingTargetEntity(entity: proto.state.PendingTargetEntity): PendingTargetEntity =
    PendingTargetEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts
    )

  private def toProtoPendingTargetEntity(entity: PendingTargetEntity): proto.state.PendingTargetEntity =
    proto.state.PendingTargetEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts
    )

  private def fromProtoProcessedTargetEntity(entity: proto.state.ProcessedTargetEntity): ProcessedTargetEntity =
    ProcessedTargetEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts
    )

  private def toProtoProcessedTargetEntity(entity: ProcessedTargetEntity): proto.state.ProcessedTargetEntity =
    proto.state.ProcessedTargetEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts
    )
}
