package stasis.client.ops.backup.stages

import java.nio.file.{Files, Path}

import akka.{Done, NotUsed}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.client.ops.backup.Providers
import stasis.client.ops.ParallelismConfig
import stasis.core.packaging.Manifest
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait FileProcessing {
  protected def targetDataset: DatasetDefinition
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def fileProcessing(implicit operation: Operation.Id): Flow[SourceFile, Either[FileMetadata, FileMetadata], NotUsed] =
    Flow[SourceFile]
      .mapAsyncUnordered(parallelism.value) {
        case file if file.hasContentChanged => processContentChanged(file).map(Left.apply)
        case file                           => processMetadataChanged(file).map(Right.apply)
      }
      .wireTap(
        metadata =>
          providers.track.fileProcessed(
            file = metadata.fold(_.path, _.path),
            contentChanged = metadata.isLeft
        )
      )

  private def processContentChanged(file: SourceFile): Future[FileMetadata] =
    for {
      staged <- stage(file)
      _ <- push(file, staged).recoverWith { case NonFatal(failure) => discardOnFailure(staged, failure) }
      _ <- discard(staged)
    } yield {
      file.currentMetadata
    }

  private def processMetadataChanged(file: SourceFile): Future[FileMetadata] =
    Future.successful(file.currentMetadata)

  private def stage(file: SourceFile): Future[Path] =
    providers.staging
      .temporary()
      .flatMap { staged =>
        FileIO
          .fromPath(file.path)
          .via(providers.compressor.compress)
          .via(providers.encryptor.encrypt(deviceSecret.toFileSecret(file.path)))
          .runWith(FileIO.toPath(staged))
          .flatMap(result => Future.fromTry(result.status))
          .map(_ => staged)
      }

  private def push(file: SourceFile, staged: Path): Future[Done] = {
    val content: Source[ByteString, NotUsed] = FileIO
      .fromPath(staged)
      .mapMaterializedValue(_ => NotUsed)

    val manifest: Manifest = Manifest(
      crate = file.currentMetadata.crate,
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
