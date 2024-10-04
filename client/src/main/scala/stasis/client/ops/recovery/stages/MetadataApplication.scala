package stasis.client.ops.recovery.stages

import scala.concurrent.ExecutionContext

import org.apache.pekko.stream.scaladsl.Flow
import org.apache.pekko.Done
import org.apache.pekko.NotUsed

import stasis.client.analysis.Metadata
import stasis.client.model.TargetEntity
import stasis.client.ops.recovery.Providers
import stasis.client.ops.Metrics
import stasis.client.ops.ParallelismConfig
import stasis.shared.ops.Operation

trait MetadataApplication {
  protected def parallelism: ParallelismConfig
  protected def providers: Providers

  protected implicit def ec: ExecutionContext

  private val metrics = providers.telemetry.metrics[Metrics.RecoveryOperation]

  def metadataApplication(implicit operation: Operation.Id): Flow[TargetEntity, Done, NotUsed] =
    Flow[TargetEntity]
      .mapAsync(parallelism.entities) { targetEntity =>
        Metadata
          .applyEntityMetadataTo(
            metadata = targetEntity.existingMetadata,
            entity = targetEntity.destinationPath
          )
          .map(_ => targetEntity)
      }
      .wireTap { targetEntity =>
        metrics.recordMetadataApplied(entity = targetEntity)
        providers.track.metadataApplied(entity = targetEntity.destinationPath)
      }
      .map(_ => Done)
}
