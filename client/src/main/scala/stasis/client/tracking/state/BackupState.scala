package stasis.client.tracking.state

import stasis.client.model.{proto, EntityMetadata, SourceEntity}
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation

import java.nio.file.{Path, Paths}
import java.time.Instant
import java.util.UUID
import scala.util.{Failure, Success, Try}

final case class BackupState(
  operation: Operation.Id,
  definition: DatasetDefinition.Id,
  started: Instant,
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

  def entitySkipped(entity: Path): BackupState =
    copy(entities = entities.copy(skipped = entities.skipped + entity))

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

  def remainingEntities(): Seq[Path] =
    completed match {
      case Some(_) =>
        Seq.empty

      case None =>
        entities.discovered
          .filterNot(entity => entities.processed.contains(entity))
          .toSeq
    }

  def asMetadataChanges: (Map[Path, EntityMetadata], Map[Path, EntityMetadata]) =
    entities.processed
      .foldLeft((Map.empty[Path, EntityMetadata], Map.empty[Path, EntityMetadata])) {
        case ((contentChanged, metadataChanged), (_, processed)) =>
          processed.metadata match {
            case Left(metadata)  => (contentChanged + (metadata.path -> metadata), metadataChanged)
            case Right(metadata) => (contentChanged, metadataChanged + (metadata.path -> metadata))
          }
      }

  override def asProgress: Operation.Progress = Operation.Progress(
    started = started,
    total = entities.discovered.size,
    processed = entities.skipped.size + entities.processed.size,
    failures = entities.failed.size + failures.size,
    completed = completed
  )
}

object BackupState {
  def start(operation: Operation.Id, definition: DatasetDefinition.Id): BackupState = BackupState(
    operation = operation,
    definition = definition,
    started = Instant.now,
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
    skipped: Set[Path],
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
      skipped = Set.empty,
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

  def toProto(state: BackupState): proto.state.BackupState =
    proto.state.BackupState(
      started = state.started.toEpochMilli,
      definition = state.definition.toString,
      entities = Some(
        proto.state.BackupEntities(
          discovered = state.entities.discovered.map(_.toAbsolutePath.toString).toSeq,
          unmatched = state.entities.unmatched,
          examined = state.entities.examined.map(_.toAbsolutePath.toString).toSeq,
          skipped = state.entities.skipped.map(_.toAbsolutePath.toString).toSeq,
          collected = state.entities.collected.map { case (k, v) =>
            k.toAbsolutePath.toString -> toProtoSourceEntity(v)
          },
          pending = state.entities.pending.map { case (k, v) =>
            k.toAbsolutePath.toString -> toProtoPendingSourceEntity(v)
          },
          processed = state.entities.processed.map { case (k, v) =>
            k.toAbsolutePath.toString -> toProtoProcessedSourceEntity(v)
          },
          failed = state.entities.failed.map { case (k, v) => k.toAbsolutePath.toString -> v }
        )
      ),
      metadataCollected = state.metadataCollected.map(_.toEpochMilli),
      metadataPushed = state.metadataPushed.map(_.toEpochMilli),
      failures = state.failures,
      completed = state.completed.map(_.toEpochMilli)
    )

  def fromProto(operation: Operation.Id, state: proto.state.BackupState): Try[BackupState] =
    state.entities match {
      case Some(entities) =>
        Try {
          BackupState(
            operation = operation,
            definition = UUID.fromString(state.definition),
            started = Instant.ofEpochMilli(state.started),
            entities = BackupState.Entities(
              discovered = entities.discovered.map(Paths.get(_)).toSet,
              unmatched = entities.unmatched,
              examined = entities.examined.map(Paths.get(_)).toSet,
              skipped = entities.skipped.map(Paths.get(_)).toSet,
              collected = entities.collected.map { case (k, v) => Paths.get(k) -> fromProtoSourceEntity(v) },
              pending = entities.pending.map { case (k, v) => Paths.get(k) -> fromProtoPendingSourceEntity(v) },
              processed = entities.processed.map { case (k, v) => Paths.get(k) -> fromProtoProcessedSourceEntity(v) },
              failed = entities.failed.map { case (k, v) => Paths.get(k) -> v }
            ),
            metadataCollected = state.metadataCollected.map(Instant.ofEpochMilli),
            metadataPushed = state.metadataPushed.map(Instant.ofEpochMilli),
            failures = state.failures,
            completed = state.completed.map(Instant.ofEpochMilli)
          )
        }

      case None =>
        Failure(new IllegalArgumentException("Expected entities in backup state but none were found"))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def fromProtoSourceEntity(entity: proto.state.SourceEntity): SourceEntity =
    SourceEntity(
      path = Paths.get(entity.path),
      existingMetadata = entity.existingMetadata.map { metadata =>
        EntityMetadata.fromProto(metadata) match {
          case Success(metadata) => metadata
          case Failure(e)        => throw e
        }
      },
      currentMetadata = entity.currentMetadata.flatMap(metadata => EntityMetadata.fromProto(metadata).toOption) match {
        case Some(metadata) => metadata
        case None           => throw new IllegalArgumentException("Expected current metadata in backup state but none was found")
      }
    )

  private def toProtoSourceEntity(entity: SourceEntity): proto.state.SourceEntity =
    proto.state.SourceEntity(
      path = entity.path.toAbsolutePath.toString,
      existingMetadata = entity.existingMetadata.map(EntityMetadata.toProto),
      currentMetadata = Some(EntityMetadata.toProto(entity.currentMetadata))
    )

  private def fromProtoPendingSourceEntity(entity: proto.state.PendingSourceEntity): PendingSourceEntity =
    PendingSourceEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts
    )

  private def toProtoPendingSourceEntity(entity: PendingSourceEntity): proto.state.PendingSourceEntity =
    proto.state.PendingSourceEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts
    )

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def fromProtoProcessedSourceEntity(entity: proto.state.ProcessedSourceEntity): ProcessedSourceEntity =
    ProcessedSourceEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts,
      metadata = entity.metadata match {
        case proto.state.ProcessedSourceEntity.Metadata.Left(metadata) =>
          EntityMetadata.fromProto(metadata) match {
            case Success(value) => Left(value)
            case Failure(e)     => throw e
          }

        case proto.state.ProcessedSourceEntity.Metadata.Right(metadata) =>
          EntityMetadata.fromProto(metadata) match {
            case Success(value) => Right(value)
            case Failure(e)     => throw e
          }

        case proto.state.ProcessedSourceEntity.Metadata.Empty =>
          throw new IllegalArgumentException("Expected entity metadata in backup state but none was found")
      }
    )

  private def toProtoProcessedSourceEntity(entity: ProcessedSourceEntity): proto.state.ProcessedSourceEntity =
    proto.state.ProcessedSourceEntity(
      expectedParts = entity.expectedParts,
      processedParts = entity.processedParts,
      metadata = entity.metadata match {
        case Left(value)  => proto.state.ProcessedSourceEntity.Metadata.Left(EntityMetadata.toProto(value))
        case Right(value) => proto.state.ProcessedSourceEntity.Metadata.Right(EntityMetadata.toProto(value))
      }
    )
}
