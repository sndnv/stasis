package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.model.{DatasetMetadata, EntityMetadata, FilesystemMetadata, TargetEntity}
import stasis.client.ops.ParallelismConfig

import scala.concurrent.{ExecutionContext, Future}

trait RecoveryCollector {
  def collect(): Source[TargetEntity, NotUsed]
}

object RecoveryCollector {
  class Default(
    targetMetadata: DatasetMetadata,
    keep: (Path, FilesystemMetadata.EntityState) => Boolean,
    destination: TargetEntity.Destination,
    metadataCollector: RecoveryMetadataCollector,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends RecoveryCollector {
    override def collect(): Source[TargetEntity, NotUsed] =
      Source(
        collectEntityMetadata(
          targetMetadata = targetMetadata,
          keep = keep,
          api = api
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
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Seq[Future[EntityMetadata]] =
    targetMetadata.filesystem.entities.collect {
      case (entity, state) if keep(entity, state) =>
        targetMetadata.require(entity = entity, api = api)
    }.toSeq
}
