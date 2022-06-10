package stasis.client.ops.backup.stages

import akka.NotUsed
import akka.stream.scaladsl.Flow
import stasis.client.collection.BackupCollector
import stasis.client.model.SourceEntity
import stasis.client.ops.Metrics
import stasis.client.ops.backup.Providers
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation

trait EntityCollection {
  protected def targetDataset: DatasetDefinition
  protected def providers: Providers

  private val metrics = providers.telemetry.metrics[Metrics.BackupOperation]

  def entityCollection(implicit operation: Operation.Id): Flow[BackupCollector, SourceEntity, NotUsed] =
    Flow[BackupCollector]
      .flatMapConcat(_.collect())
      .wireTap { entity =>
        metrics.recordEntityExamined(entity = entity)
        providers.track.entityExamined(
          entity = entity.path,
          metadataChanged = entity.hasChanged,
          contentChanged = entity.hasContentChanged
        )
      }
      .filter(_.hasChanged)
      .wireTap { entity =>
        metrics.recordEntityCollected(entity = entity)
        providers.track.entityCollected(entity = entity.path)
      }
}
