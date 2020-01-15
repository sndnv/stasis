package stasis.client.ops.recovery.stages

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.RecoveryCollector
import stasis.client.model.SourceFile
import stasis.client.ops.recovery.Providers
import stasis.shared.ops.Operation

trait FileCollection {
  protected def collector: RecoveryCollector
  protected def providers: Providers

  def fileCollection(implicit operation: Operation.Id): Source[SourceFile, NotUsed] =
    collector
      .collect()
      .log(
        name = "File Collection",
        extract = { file =>
          val metadata = if (file.hasChanged) "changed" else "not changed"
          val content = if (file.hasContentChanged) "changed" else "not changed"
          s"[${file.path}] - Metadata: [$metadata]; Content: [$content]"
        }
      )
      .filter(_.hasChanged)
      .wireTap(file => providers.track.fileCollected(file = file.path))
}
