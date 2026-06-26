package stasis.client.ops.recovery.stages

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source

import stasis.client.model.DatasetMetadata
import stasis.client.model.FilesystemMetadata
import stasis.client.model.TargetEntity
import stasis.client.ops.Metrics
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation

trait EntityCollection {
  protected def targetMetadata: DatasetMetadata
  protected def keep: (String, FilesystemMetadata.EntityState) => Boolean
  protected def destination: TargetEntity.Destination
  protected def providers: Providers
  protected def parallelism: ParallelismConfig

  protected implicit def mat: Materializer

  private val metrics = providers.telemetry.metrics[Metrics.RecoveryOperation]

  def entityCollection(implicit operation: Operation.Id): Source[TargetEntity, NotUsed] =
    Source(providers.kinds.toList)
      .flatMapConcat(_.collector(targetMetadata, keep, destination, providers, parallelism).collect(providers.filesystem))
      .wireTap { entity =>
        metrics.recordEntityExamined(entity = entity)

        providers.track.entityExamined(
          entity = entity.ref,
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
