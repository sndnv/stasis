package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.model.{DatasetMetadata, FileMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig

import scala.concurrent.{ExecutionContext, Future}

trait BackupCollector {
  def collect(): Source[SourceFile, NotUsed]
}

object BackupCollector {
  class Default(
    files: List[Path],
    latestMetadata: Option[DatasetMetadata],
    metadataCollector: BackupMetadataCollector,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends BackupCollector {
    override def collect(): Source[SourceFile, NotUsed] =
      Source(
        collectFileMetadata(
          files = files,
          latestMetadata = latestMetadata,
          api = api
        ).toList
      ).mapAsyncUnordered(parallelism.value) {
        case (file, fileMetadataFuture) =>
          for {
            fileMetadata <- fileMetadataFuture
            sourceFile <- metadataCollector.collect(file = file, existingMetadata = fileMetadata)
          } yield {
            sourceFile
          }
      }
  }

  def collectFileMetadata(
    files: List[Path],
    latestMetadata: Option[DatasetMetadata],
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Seq[(Path, Future[Option[FileMetadata]])] =
    latestMetadata match {
      case Some(metadata) => files.map(file => (file, metadata.collect(file = file, api = api)))
      case None           => files.map(file => (file, Future.successful(None)))
    }
}
