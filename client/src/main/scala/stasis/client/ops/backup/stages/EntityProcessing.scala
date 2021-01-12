package stasis.client.ops.backup.stages

import java.nio.file.{Files, Path, Paths}

import akka.stream.scaladsl.{FileIO, Flow, Source, SubFlow}
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.client.encryption.secrets.{DeviceFileSecret, DeviceSecret}
import stasis.client.model.{EntityMetadata, SourceEntity}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.backup.Providers
import stasis.core.packaging.{Crate, Manifest}
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation

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

  private val maximumPartSize = math.min(maxPartSize, providers.encryptor.maxPlaintextSize)

  def entityProcessing(implicit operation: Operation.Id): Flow[SourceEntity, Either[EntityMetadata, EntityMetadata], NotUsed] =
    Flow[SourceEntity]
      .mapAsyncUnordered(parallelism.value) {
        case entity if entity.hasContentChanged => processContentChanged(entity).map(Left.apply)
        case entity                             => processMetadataChanged(entity).map(Right.apply)
      }
      .wireTap(metadata =>
        providers.track.entityProcessed(
          entity = metadata.fold(_.path, _.path),
          contentChanged = metadata.isLeft
        )
      )

  private def processContentChanged(entity: SourceEntity): Future[EntityMetadata] =
    for {
      file <- EntityProcessing.expectFileMetadata(entity)
      staged <- stage(entity.path)
      crates <- push(staged)
      _ <- discard(staged)
    } yield {
      file.copy(crates = crates.toMap)
    }

  private def processMetadataChanged(entity: SourceEntity): Future[EntityMetadata] =
    Future.successful(entity.currentMetadata)

  private def stage(entity: Path): Future[Seq[(Path, Path)]] = {
    def createPartSecret(partId: Int): DeviceFileSecret = {
      val partPath = Paths.get(s"${entity.toAbsolutePath.toString}_${partId.toString}")
      deviceSecret.toFileSecret(partPath)
    }

    implicit val prv: Providers = providers

    FileIO
      .fromPath(f = entity, chunkSize = maxChunkSize)
      .compress()
      .partition(withMaximumPartSize = maximumPartSize)
      .stage(withPartSecret = createPartSecret)
  }

  private def push(staged: Seq[(Path, Path)]): Future[Seq[(Path, Crate.Id)]] =
    Future
      .sequence(
        staged.map { case (partFile, staged) =>
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
            .push(manifest, content)
            .map(_ => (partFile, crate))
        }
      )
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
}
