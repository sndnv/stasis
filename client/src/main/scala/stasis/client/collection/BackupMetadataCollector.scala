package stasis.client.collection

import java.nio.file.Path

import akka.stream.Materializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.{EntityMetadata, SourceEntity}

import scala.concurrent.Future

trait BackupMetadataCollector {
  def collect(entity: Path, existingMetadata: Option[EntityMetadata]): Future[SourceEntity]
}

object BackupMetadataCollector {
  class Default(checksum: Checksum)(implicit mat: Materializer) extends BackupMetadataCollector {
    override def collect(entity: Path, existingMetadata: Option[EntityMetadata]): Future[SourceEntity] =
      Metadata.collectSource(checksum = checksum, entity = entity, existingMetadata = existingMetadata)
  }

  object Default {
    def apply(checksum: Checksum)(implicit mat: Materializer): Default = new Default(checksum)
  }
}
