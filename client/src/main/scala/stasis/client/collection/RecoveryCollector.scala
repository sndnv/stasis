package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.model.{DatasetMetadata, FileMetadata, FilesystemMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig
import stasis.shared.model.datasets.DatasetEntry

import scala.concurrent.{ExecutionContext, Future}

trait RecoveryCollector {
  def collect(): Source[SourceFile, NotUsed]
}

object RecoveryCollector {
  class Default(
    targetMetadata: DatasetMetadata,
    keep: (Path, FilesystemMetadata.FileState) => Boolean,
    metadataCollector: RecoveryMetadataCollector,
    getMetadataForEntry: DatasetEntry.Id => Future[DatasetMetadata]
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends RecoveryCollector {
    override def collect(): Source[SourceFile, NotUsed] =
      Source(
        collectFileMetadata(
          targetMetadata = targetMetadata,
          keep = keep,
          getMetadataForEntry = getMetadataForEntry
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
    getMetadataForEntry: DatasetEntry.Id => Future[DatasetMetadata]
  )(implicit ec: ExecutionContext): Seq[Future[FileMetadata]] =
    targetMetadata.filesystem.files.collect {
      case (file, state) if keep(file, state) =>
        state match {
          case FilesystemMetadata.FileState.New | FilesystemMetadata.FileState.Updated =>
            targetMetadata.contentChanged.get(file).orElse(targetMetadata.metadataChanged.get(file)) match {
              case Some(metadata) =>
                Future.successful(metadata)

              case None =>
                Future.failed(
                  new IllegalArgumentException(s"Metadata for file [${file.toAbsolutePath}] not found")
                )
            }

          case FilesystemMetadata.FileState.Existing(entry) =>
            for {
              entryMetadata <- getMetadataForEntry(entry)
              fileMetadata <- entryMetadata.contentChanged
                .get(file)
                .orElse(entryMetadata.metadataChanged.get(file)) match {
                case Some(fileMetadata) =>
                  Future.successful(fileMetadata)

                case None =>
                  Future.failed(
                    new IllegalArgumentException(
                      s"Expected metadata for file [${file.toAbsolutePath}] but none was found in metadata for entry [$entry]"
                    )
                  )
              }
            } yield {
              fileMetadata
            }
        }
    }.toSeq
}
