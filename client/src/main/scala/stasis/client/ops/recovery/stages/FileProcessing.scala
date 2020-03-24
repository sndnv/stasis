package stasis.client.ops.recovery.stages

import akka.{Done, NotUsed}
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{FileMetadata, TargetFile}
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

  def fileProcessing(implicit operation: Operation.Id): Flow[TargetFile, FileMetadata, NotUsed] =
    Flow[TargetFile]
      .mapAsync(parallelism.value) { targetFile =>
        if (targetFile.hasContentChanged) {
          for {
            source <- pull(targetFile.existingMetadata)
            _ <- destage(source, targetFile.existingMetadata)
          } yield {
            targetFile.existingMetadata
          }
        } else {
          Future.successful(targetFile.existingMetadata)
        }
      }
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
