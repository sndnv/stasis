package stasis.client.ops.recovery.stages

import akka.stream.scaladsl.Flow
import akka.{Done, NotUsed}
import stasis.client.analysis.Metadata
import stasis.client.model.FileMetadata
import stasis.client.ops.ParallelismConfig

import scala.concurrent.ExecutionContext

trait MetadataApplication {
  protected def parallelism: ParallelismConfig

  protected implicit def ec: ExecutionContext

  def metadataApplication: Flow[FileMetadata, Done, NotUsed] =
    Flow[FileMetadata]
      .log(
        name = "Metadata Application",
        extract = metadata => s"Applying metadata to file: [${metadata.path}]"
      )
      .mapAsync(parallelism.value)(Metadata.applyFileMetadata)
}
