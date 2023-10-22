package stasis.client.collection

import org.apache.pekko.stream.Materializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.compression.Compression
import stasis.client.model.{EntityMetadata, SourceEntity}

import java.nio.file.Path
import scala.concurrent.Future

trait BackupMetadataCollector {
  def collect(entity: Path, existingMetadata: Option[EntityMetadata]): Future[SourceEntity]
}

object BackupMetadataCollector {
  class Default(checksum: Checksum, compression: Compression)(implicit mat: Materializer) extends BackupMetadataCollector {
    override def collect(entity: Path, existingMetadata: Option[EntityMetadata]): Future[SourceEntity] =
      Metadata.collectSource(
        checksum = checksum,
        compression = compression,
        entity = entity,
        existingMetadata = existingMetadata
      )
  }

  object Default {
    def apply(checksum: Checksum, compression: Compression)(implicit mat: Materializer): Default =
      new Default(checksum, compression)
  }
}
