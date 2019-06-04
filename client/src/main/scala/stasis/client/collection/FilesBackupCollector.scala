package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.analysis.Metadata
import stasis.client.model.SourceFile
import stasis.client.ops.ParallelismConfig

class FilesBackupCollector(
  files: List[Path],
  metadata: Metadata
)(implicit parallelism: ParallelismConfig)
    extends BackupCollector {
  override def collect(): Source[SourceFile, NotUsed] =
    Source(files).mapAsyncUnordered(parallelism.value)(metadata.collect)
}
