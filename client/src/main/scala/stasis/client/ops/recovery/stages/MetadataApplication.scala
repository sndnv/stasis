package stasis.client.ops.recovery.stages

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import stasis.client.analysis.Metadata
import stasis.client.model.TargetFile
import stasis.client.ops.ParallelismConfig
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation

import scala.concurrent.ExecutionContext

trait MetadataApplication {
  protected def parallelism: ParallelismConfig
  protected def providers: Providers

  protected implicit def ec: ExecutionContext

  def metadataApplication(implicit operation: Operation.Id): Flow[TargetFile, Done, NotUsed] =
    Flow[TargetFile]
      .mapAsync(parallelism.value) { targetFile =>
        Metadata
          .applyFileMetadataTo(
            metadata = targetFile.existingMetadata,
            file = targetFile.destinationPath
          )
          .map(_ => targetFile)
      }
      .wireTap(targetFile => providers.track.metadataApplied(file = targetFile.destinationPath))
      .map(_ => Done)
}
