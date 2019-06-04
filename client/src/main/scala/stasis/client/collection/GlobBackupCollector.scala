package stasis.client.collection

import akka.NotUsed
import akka.stream.scaladsl.Source
import better.files._
import stasis.client.analysis.Metadata
import stasis.client.collection.GlobBackupCollector.GlobRule
import stasis.client.model.SourceFile
import stasis.client.ops.ParallelismConfig

class GlobBackupCollector(
  rules: Seq[GlobRule],
  metadata: Metadata
)(implicit parallelism: ParallelismConfig)
    extends BackupCollector {
  override def collect(): Source[SourceFile, NotUsed] =
    Source(rules.toList)
      .flatMapConcat { rule =>
        Source(File(rule.directory).glob(rule.pattern).toList)
      }
      .mapAsyncUnordered(parallelism.value)(file => metadata.collect(file.path))
}

object GlobBackupCollector {
  final case class GlobRule(directory: String, pattern: String)
}
