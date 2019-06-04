package stasis.client.collection

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.analysis.Metadata
import stasis.client.model.{FileMetadata, SourceFile}
import stasis.client.ops.ParallelismConfig

class FileMetadataRecoveryCollector(
  filesMetadata: Seq[FileMetadata],
  metadata: Metadata
)(implicit parallelism: ParallelismConfig)
    extends RecoveryCollector {
  override def collect(): Source[SourceFile, NotUsed] =
    Source(filesMetadata.toList)
      .mapAsync(parallelism.value) { fileMetadata =>
        metadata.collect(file = fileMetadata.path, existingMetadata = Some(fileMetadata))
      }
}
