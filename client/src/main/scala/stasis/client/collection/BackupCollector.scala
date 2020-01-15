package stasis.client.collection

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.model.SourceFile
import stasis.client.ops.ParallelismConfig

trait BackupCollector {
  def collect(): Source[SourceFile, NotUsed]
}

object BackupCollector {
  class Default(
    files: List[Path],
    metadataCollector: BackupMetadataCollector
  )(implicit parallelism: ParallelismConfig)
      extends BackupCollector {
    override def collect(): Source[SourceFile, NotUsed] =
      Source(files).mapAsyncUnordered(parallelism.value)(metadataCollector.collect)
  }
}
