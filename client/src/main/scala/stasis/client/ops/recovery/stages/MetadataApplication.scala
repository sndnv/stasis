package stasis.client.ops.recovery.stages

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import stasis.client.analysis.Metadata
import stasis.client.model.TargetEntity
import stasis.client.ops.{Metrics, ParallelismConfig}
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation

import scala.concurrent.ExecutionContext

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
