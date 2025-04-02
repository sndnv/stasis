package stasis.client.collection

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.api.clients.Clients
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityMetadata
import stasis.client.model.FilesystemMetadata
import stasis.client.model.TargetEntity
import stasis.client.ops.ParallelismConfig

trait RecoveryCollector {
  def collect(): Source[TargetEntity, NotUsed]
}

object RecoveryCollector {
  class Default(
    targetMetadata: DatasetMetadata,
    keep: (Path, FilesystemMetadata.EntityState) => Boolean,
    destination: TargetEntity.Destination,
    metadataCollector: RecoveryMetadataCollector,
    clients: Clients
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends RecoveryCollector {
    override def collect(): Source[TargetEntity, NotUsed] =
      Source(
        collectEntityMetadata(
          targetMetadata = targetMetadata,
          keep = keep,
          clients = clients
        ).toList
      ).mapAsync(parallelism.entities) { entityMetadataFuture =>
        for {
          entityMetadata <- entityMetadataFuture
          targetEntity <- metadataCollector.collect(
            entity = entityMetadata.path,
            destination = destination,
            existingMetadata = entityMetadata
          )
        } yield {
          targetEntity
        }
      }
  }

  def collectEntityMetadata(
    targetMetadata: DatasetMetadata,
    keep: (Path, FilesystemMetadata.EntityState) => Boolean,
    clients: Clients
  )(implicit ec: ExecutionContext): Seq[Future[EntityMetadata]] =
    targetMetadata.filesystem.entities.collect {
      case (entity, state) if keep(entity, state) =>
        targetMetadata.require(entity = entity, clients = clients)
    }.toSeq
}
