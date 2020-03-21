package stasis.client.collection

import java.nio.file.Path

import akka.stream.Materializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.{DatasetMetadata, FileMetadata, SourceFile}

import scala.concurrent.Future

trait BackupMetadataCollector {
  def collect(file: Path, existingMetadata: Option[FileMetadata]): Future[SourceFile]
}

object BackupMetadataCollector {
  class Default(checksum: Checksum)(implicit mat: Materializer) extends BackupMetadataCollector {
    override def collect(file: Path, existingMetadata: Option[FileMetadata]): Future[SourceFile] =
      Metadata.collect(checksum = checksum, file = file, existingMetadata = existingMetadata)
  }
}
