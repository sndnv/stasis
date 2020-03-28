package stasis.client.ops.recovery.stages

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Flow, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stasis.client.encryption.secrets.DeviceSecret
import stasis.client.model.{FileMetadata, TargetFile}
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.core.routing.exceptions.PullFailure
import stasis.shared.ops.Operation

import scala.concurrent.{ExecutionContext, Future}

trait FileProcessing {
  protected def deviceSecret: DeviceSecret
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer
  protected implicit def ec: ExecutionContext

  def fileProcessing(implicit operation: Operation.Id): Flow[TargetFile, TargetFile, NotUsed] =
    Flow[TargetFile]
      .mapAsync(parallelism.value) { targetFile =>
        if (targetFile.hasContentChanged) {
          for {
            source <- pull(fileMetadata = targetFile.existingMetadata)
            _ <- destage(source, targetFile)
          } yield {
            targetFile
          }
        } else {
          Future.successful(targetFile)
        }
      }
      .wireTap(targetFile => providers.track.fileProcessed(file = targetFile.destinationPath))

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

  private def destage(
    source: Source[ByteString, NotUsed],
    file: TargetFile
  ): Future[Done] =
    providers.staging
      .temporary()
      .flatMap { staged =>
        source
          .via(providers.decryptor.decrypt(deviceSecret.toFileSecret(file.originalPath)))
          .via(providers.decompressor.decompress)
          .runWith(FileIO.toPath(staged))
          .flatMap(result => Future.fromTry(result.status))
          .flatMap(_ => providers.staging.destage(from = staged, to = file.destinationPath))
      }
}
