package stasis.client.ops.recovery.stages

import akka.{Done, NotUsed}
import akka.stream.scaladsl.Flow
import stasis.client.analysis.Metadata
import stasis.client.model.FileMetadata
import stasis.client.ops.recovery.Providers
import stasis.client.ops.ParallelismConfig
import stasis.shared.ops.Operation

import scala.concurrent.ExecutionContext

trait MetadataApplication {
  protected def parallelism: ParallelismConfig
  protected def providers: Providers

  protected implicit def ec: ExecutionContext

  def metadataApplication(implicit operation: Operation.Id): Flow[FileMetadata, Done, NotUsed] =
    Flow[FileMetadata]
      .mapAsync(parallelism.value)(metadata => Metadata.applyFileMetadata(metadata).map(_ => metadata))
      .log(
        name = "Metadata Application",
        extract = metadata => s"Applied metadata to file: [${metadata.path}]"
      )
      .wireTap(metadata => providers.track.metadataApplied(file = metadata.path))
      .map(_ => Done)
}
