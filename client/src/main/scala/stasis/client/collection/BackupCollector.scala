package stasis.client.collection

import java.nio.file.Path

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.api.clients.Clients
import stasis.client.model.DatasetMetadata
import stasis.client.model.EntityMetadata
import stasis.client.model.SourceEntity
import stasis.client.ops.ParallelismConfig

trait BackupCollector {
  def collect(): Source[SourceEntity, NotUsed]
}

object BackupCollector {
  class Default(
    entities: List[Path],
    latestMetadata: Option[DatasetMetadata],
    metadataCollector: BackupMetadataCollector,
    clients: Clients
  )(implicit ec: ExecutionContext, parallelism: ParallelismConfig)
      extends BackupCollector {
    override def collect(): Source[SourceEntity, NotUsed] =
      Source(
        collectEntityMetadata(
          entities = entities,
          latestMetadata = latestMetadata,
          clients = clients
        ).toList
      ).mapAsyncUnordered(parallelism.entities) { case (entity, entityMetadataFuture) =>
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
    clients: Clients
  )(implicit ec: ExecutionContext): Seq[(Path, Future[Option[EntityMetadata]])] =
    latestMetadata match {
      case Some(metadata) => entities.map(entity => (entity, metadata.collect(entity = entity, clients = clients)))
      case None           => entities.map(entity => (entity, Future.successful(None)))
    }
}
