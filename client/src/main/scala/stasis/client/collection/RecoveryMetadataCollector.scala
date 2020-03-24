package stasis.client.collection

import java.nio.file.Path

import akka.stream.Materializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.{FileMetadata, TargetFile}

import scala.concurrent.Future

trait RecoveryMetadataCollector {
  def collect(file: Path, existingMetadata: FileMetadata): Future[TargetFile]
}

object RecoveryMetadataCollector {
  class Default(checksum: Checksum)(implicit mat: Materializer) extends RecoveryMetadataCollector {
    override def collect(file: Path, existingMetadata: FileMetadata): Future[TargetFile] =
      Metadata.collectTarget(checksum = checksum, file = file, existingMetadata = existingMetadata)
  }
}
