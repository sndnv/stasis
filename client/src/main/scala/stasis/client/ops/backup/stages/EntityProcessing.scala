package stasis.client.ops.backup.stages

import java.nio.file.{Files, Path}

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.client.encryption.secrets.DeviceSecret
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

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def entityProcessing(implicit operation: Operation.Id): Flow[SourceEntity, Either[EntityMetadata, EntityMetadata], NotUsed] =
    Flow[SourceEntity]
      .mapAsyncUnordered(parallelism.value) {
        case entity if entity.hasContentChanged => processContentChanged(entity).map(Left.apply)
        case entity                             => processMetadataChanged(entity).map(Right.apply)
      }
      .wireTap(
        metadata =>
          providers.track.entityProcessed(
            entity = metadata.fold(_.path, _.path),
            contentChanged = metadata.isLeft
        )
      )

  private def processContentChanged(entity: SourceEntity): Future[EntityMetadata] =
    for {
      crate <- Future.fromTry(entity.currentMetadata.collectCrate)
      staged <- stage(entity.path)
      _ <- push(crate, staged).recoverWith { case NonFatal(failure) => discardOnFailure(staged, failure) }
      _ <- discard(staged)
    } yield {
      entity.currentMetadata
    }

  private def processMetadataChanged(entity: SourceEntity): Future[EntityMetadata] =
    Future.successful(entity.currentMetadata)

  private def stage(entity: Path): Future[Path] =
    providers.staging
      .temporary()
      .flatMap { staged =>
        FileIO
          .fromPath(entity)
          .via(providers.compressor.compress)
          .via(providers.encryptor.encrypt(deviceSecret.toFileSecret(entity)))
          .runWith(FileIO.toPath(staged))
          .flatMap(result => Future.fromTry(result.status))
          .map(_ => staged)
      }

  private def push(crate: Crate.Id, staged: Path): Future[Done] = {
    val content: Source[ByteString, NotUsed] = FileIO
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
  }

  private def discardOnFailure(staged: Path, failure: Throwable): Future[Done] =
    discard(staged).flatMap(_ => Future.failed(failure))

  private def discard(staged: Path): Future[Done] = providers.staging.discard(staged)
}
