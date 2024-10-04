package stasis.client.ops.recovery.stages

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.collection.RecoveryCollector
import stasis.client.model.TargetEntity
import stasis.client.ops.Metrics
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation

trait EntityCollection {
  protected def collector: RecoveryCollector
  protected def providers: Providers

  private val metrics = providers.telemetry.metrics[Metrics.RecoveryOperation]

  def entityCollection(implicit operation: Operation.Id): Source[TargetEntity, NotUsed] =
    collector
      .collect()
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
        providers.track.entityCollected(entity = entity)
      }
}
