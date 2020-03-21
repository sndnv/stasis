package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.model.{DatasetMetadata, FileMetadata, FilesystemMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig

import scala.concurrent.{ExecutionContext, Future}

trait RecoveryCollector {
  def collect(): Source[SourceFile, NotUsed]
}

object RecoveryCollector {
  class Default(
    targetMetadata: DatasetMetadata,
    keep: (Path, FilesystemMetadata.FileState) => Boolean,
    metadataCollector: RecoveryMetadataCollector,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends RecoveryCollector {
    override def collect(): Source[SourceFile, NotUsed] =
      Source(
        collectFileMetadata(
          targetMetadata = targetMetadata,
          keep = keep,
          api = api
        ).toList
      ).mapAsync(parallelism.value) { fileMetadataFuture =>
        for {
          fileMetadata <- fileMetadataFuture
          sourceFile <- metadataCollector.collect(file = fileMetadata.path, existingMetadata = fileMetadata)
        } yield {
          sourceFile
        }
      }
  }

  def collectFileMetadata(
    targetMetadata: DatasetMetadata,
    keep: (Path, FilesystemMetadata.FileState) => Boolean,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Seq[Future[FileMetadata]] =
    targetMetadata.filesystem.files.collect {
      case (file, state) if keep(file, state) =>
        targetMetadata.require(file = file, api = api)
    }.toSeq
}
