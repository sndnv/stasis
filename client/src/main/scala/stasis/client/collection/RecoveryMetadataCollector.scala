package stasis.client.collection

import java.nio.file.Path

import akka.stream.Materializer
import stasis.client.analysis.{Checksum, Metadata}
import stasis.client.model.{EntityMetadata, TargetEntity}

import scala.concurrent.Future

trait RecoveryMetadataCollector {
  def collect(
    entity: Path,
    destination: TargetEntity.Destination,
    existingMetadata: EntityMetadata
  ): Future[TargetEntity]
}

object RecoveryMetadataCollector {
  class Default(checksum: Checksum)(implicit mat: Materializer) extends RecoveryMetadataCollector {
    override def collect(
      entity: Path,
      destination: TargetEntity.Destination,
      existingMetadata: EntityMetadata
    ): Future[TargetEntity] =
      Metadata.collectTarget(
        checksum = checksum,
        entity = entity,
        destination = destination,
        existingMetadata = existingMetadata
      )
  }
}
