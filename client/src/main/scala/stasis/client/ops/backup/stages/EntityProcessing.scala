package stasis.client.ops.backup.stages

import akka.stream.scaladsl.{FileIO, Flow, Sink, Source, SubFlow}
import akka.stream.{IOResult, Materializer, SharedKillSwitch}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceSecret}
import stasis.client.model.{EntityMetadata, SourceEntity}
import stasis.client.ops.backup.Providers
import stasis.client.ops.exceptions.{EntityProcessingFailure, OperationStopped}
import stasis.client.ops.{Metrics, ParallelismConfig}
import stasis.core.packaging.{Crate, Manifest}
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait EntityProcessing {
  protected def targetDataset: DatasetDefinition
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected def maxChunkSize: Int
  protected def maxPartSize: Long

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  private val metrics = providers.telemetry.metrics[Metrics.BackupOperation]

  private val maximumPartSize = math.min(maxPartSize, providers.encryptor.maxPlaintextSize)

  def entityProcessing(implicit
    operation: Operation.Id,
    killSwitch: SharedKillSwitch
  ): Flow[SourceEntity, Either[EntityMetadata, EntityMetadata], NotUsed] =
    Flow[SourceEntity]
      .mapAsyncUnordered(parallelism.entities) {
        case entity if entity.hasContentChanged => processContentChanged(entity).map(Left.apply)
        case entity                             => processMetadataChanged(entity).map(Right.apply)
      }
      .wireTap { metadata =>
        metrics.recordEntityProcessed(metadata = metadata)
        providers.track.entityProcessed(
          entity = metadata.fold(_.path, _.path),
          metadata = metadata
        )
      }

  private def processContentChanged(
    entity: SourceEntity
  )(implicit operation: Operation.Id, killSwitch: SharedKillSwitch): Future[EntityMetadata] = {
    val result = for {
      file <- EntityProcessing.expectFileMetadata(entity)
      staged <- stage(entity)
      crates <- push(staged)
      _ <- discard(staged)
    } yield {
      file.copy(crates = crates.toMap)
    }

    result.recoverWith {
      case NonFatal(e: OperationStopped) => Future.failed(e)
      case NonFatal(e)                   => Future.failed(EntityProcessingFailure(entity = entity.path, cause = e))
    }
  }

  private def processMetadataChanged(
    entity: SourceEntity
  )(implicit operation: Operation.Id): Future[EntityMetadata] = {
    providers.track.entityProcessingStarted(entity = entity.path, expectedParts = 0)
    Future.successful(entity.currentMetadata)
  }

  private def stage(
    entity: SourceEntity
  )(implicit operation: Operation.Id, killSwitch: SharedKillSwitch): Future[Seq[(Path, Path)]] = {
    providers.track.entityProcessingStarted(
      entity = entity.path,
      expectedParts = EntityProcessing.expectedParts(entity, maximumPartSize)
    )

    def createPartSecret(partId: Int): DeviceFileSecret = {
      val partPath = Paths.get(s"${entity.path.toAbsolutePath.toString}__part=${partId.toString}")
      deviceSecret.toFileSecret(partPath)
    }

    def recordPartProcessed(): Unit =
      providers.track.entityPartProcessed(entity = entity.path)

    implicit val prv: Providers = providers

    val compressor = providers.compression.encoderFor(entity)

    FileIO
      .fromPath(f = entity.path, chunkSize = maxChunkSize)
      .via(killSwitch.flow)
      .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "read", bytes.length))
      .compress(compressor = compressor)
      .wireTap(bytes => metrics.recordEntityChunkProcessed(step = "compressed", extra = compressor.name, bytes = bytes.length))
      .partition(withMaximumPartSize = maximumPartSize)
      .stage(withPartSecret = createPartSecret, onPartStaged = recordPartProcessed)
  }

  private def push(staged: Seq[(Path, Path)]): Future[Seq[(Path, Crate.Id)]] =
    Source(staged.toList)
      .mapAsync(parallelism = parallelism.entityParts) { case (partFile, staged) =>
        val crate = Crate.generateId()

        val content: Source[ByteString, NotUsed] =
          FileIO
            .fromPath(staged)
            .mapMaterializedValue(_ => NotUsed)

        val manifest: Manifest = Manifest(
          crate = crate,
          origin = providers.clients.core.self,
          source = providers.clients.core.self,
          size = Files.size(staged),
          copies = targetDataset.redundantCopies
        )

        providers.clients.core
          .push(
            manifest = manifest,
            content = content.wireTap(bytes => metrics.recordEntityChunkProcessed(step = "pushed", bytes = bytes.length))
          )
          .map(_ => (partFile, crate))
      }
      .runWith(Sink.seq)
      .recoverWith(discardOnPushFailure(staged))

  private def discardOnPushFailure[T](staged: Seq[(Path, Path)]): PartialFunction[Throwable, Future[T]] = { case NonFatal(e) =>
    discard(staged).flatMap(_ => Future.failed(e))
  }

  private def discard(staged: Seq[(Path, Path)]): Future[Done] =
    Future
      .sequence(
        staged.map { case (_, staged) =>
          providers.staging.discard(staged)
        }
      )
      .map(_ => Done)

  private type EntitySource = Source[ByteString, Future[IOResult]]
  private type EntitySubFlow = SubFlow[ByteString, Future[IOResult], EntitySource#Repr, EntitySource#Closed]

  private implicit class CompressedByteStringSource(source: EntitySource) extends internal.CompressedByteStringSource(source)
  private implicit class PartitionedByteStringSource(source: EntitySource) extends internal.PartitionedByteStringSource(source)
  private implicit class StagedSubFlow(subFlow: EntitySubFlow) extends internal.StagedSubFlow(subFlow)
}

object EntityProcessing {
  def expectFileMetadata(entity: SourceEntity): Future[EntityMetadata.File] =
    entity.currentMetadata match {
      case file: EntityMetadata.File =>
        Future.successful(file)

      case directory: EntityMetadata.Directory =>
        Future.failed(
          new IllegalArgumentException(
            s"Expected metadata for file but directory metadata for [${directory.path.toString}] provided"
          )
        )
    }

  def expectedParts(entity: SourceEntity, withMaximumPartSize: Long): Int = {
    require(withMaximumPartSize > 0, s"Invalid [maximumPartSize] provided: [${withMaximumPartSize.toString}]")

    entity.currentMetadata match {
      case file: EntityMetadata.File if entity.hasContentChanged =>
        val fullParts = (file.size / withMaximumPartSize).toInt
        if (file.size % withMaximumPartSize == 0) fullParts else fullParts + 1

      case _ => 0
    }
  }
}
