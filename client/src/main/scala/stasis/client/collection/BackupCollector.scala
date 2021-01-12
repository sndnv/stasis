package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.model.{DatasetMetadata, EntityMetadata, SourceEntity}
import stasis.client.ops.ParallelismConfig

import scala.concurrent.{ExecutionContext, Future}

trait BackupCollector {
  def collect(): Source[SourceEntity, NotUsed]
}

object BackupCollector {
  class Default(
    entities: List[Path],
    latestMetadata: Option[DatasetMetadata],
    metadataCollector: BackupMetadataCollector,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends BackupCollector {
    override def collect(): Source[SourceEntity, NotUsed] =
      Source(
        collectEntityMetadata(
          entities = entities,
          latestMetadata = latestMetadata,
          api = api
        ).toList
      ).mapAsyncUnordered(parallelism.value) { case (entity, entityMetadataFuture) =>
        for {
          entityMetadata <- entityMetadataFuture
          sourceEntity <- metadataCollector.collect(entity = entity, existingMetadata = entityMetadata)
        } yield {
          sourceEntity
        }
      }
  }

  def collectEntityMetadata(
    entities: List[Path],
    latestMetadata: Option[DatasetMetadata],
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Seq[(Path, Future[Option[EntityMetadata]])] =
    latestMetadata match {
      case Some(metadata) => entities.map(entity => (entity, metadata.collect(entity = entity, api = api)))
      case None           => entities.map(entity => (entity, Future.successful(None)))
    }
}
