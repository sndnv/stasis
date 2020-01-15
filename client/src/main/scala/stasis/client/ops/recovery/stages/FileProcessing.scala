package stasis.client.ops.recovery.stages

import akka.{Done, NotUsed}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.client.ops.recovery.Providers
import stasis.client.ops.ParallelismConfig
import stasis.core.routing.exceptions.PullFailure
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}

trait FileProcessing {
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def fileProcessing(implicit operation: Operation.Id): Flow[SourceFile, FileMetadata, NotUsed] =
    Flow[SourceFile]
      .mapAsync(parallelism.value) { sourceFile =>
        sourceFile.existingMetadata match {
          case Some(fileMetadata) =>
            if (sourceFile.hasContentChanged) {
              for {
                source <- pull(fileMetadata)
                _ <- destage(source, fileMetadata)
              } yield {
                fileMetadata
              }
            } else {
              Future.successful(fileMetadata)
            }

          case None =>
            Future.failed(
              new IllegalStateException(
                s"Expected existing metadata for file [${sourceFile.path}] but none was provided"
              )
            )
        }
      }
      .log(
        name = "File Processing",
        extract = metadata => s"Processed file [${metadata.path}]"
      )
      .wireTap(metadata => providers.track.fileProcessed(file = metadata.path))

  private def pull(fileMetadata: FileMetadata): Future[Source[ByteString, NotUsed]] =
    providers.clients.core
      .pull(fileMetadata.crate)
      .flatMap {
        case Some(source) =>
          Future.successful(source)

        case None =>
          Future.failed(
            PullFailure(s"Failed to pull crate [${fileMetadata.crate}] for file [${fileMetadata.path}]")
          )
      }

  private def destage(source: Source[ByteString, NotUsed], fileMetadata: FileMetadata): Future[Done] =
    providers.staging
      .temporary()
      .flatMap { staged =>
        source
          .via(providers.decryptor.decrypt(deviceSecret.toFileSecret(fileMetadata.path)))
          .via(providers.decompressor.decompress)
          .runWith(FileIO.toPath(staged))
          .flatMap(result => Future.fromTry(result.status))
          .flatMap(_ => providers.staging.destage(from = staged, to = fileMetadata.path))
      }
}
