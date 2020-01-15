package stasis.client.collection

import java.nio.file.Path

import akka.stream.Materializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.{DatasetMetadata, SourceFile}

import scala.concurrent.Future

trait BackupMetadataCollector {
  def collect(file: Path): Future[SourceFile]
}

object BackupMetadataCollector {
  class Default(checksum: Checksum, latestMetadata: Option[DatasetMetadata])(implicit mat: Materializer)
      extends BackupMetadataCollector {
    override def collect(file: Path): Future[SourceFile] =
      Metadata.collect(
        checksum = checksum,
        file = file,
        existingMetadata = latestMetadata.flatMap { metadata =>
          metadata.contentChanged.get(file).orElse(metadata.metadataChanged.get(file))
        }
      )
  }
}
