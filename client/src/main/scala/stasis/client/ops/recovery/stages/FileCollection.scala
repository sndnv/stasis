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
      .wireTap(
        file =>
          providers.track.fileExamined(
            file = file.path,
            metadataChanged = file.hasChanged,
            contentChanged = file.hasContentChanged
        )
      )
      .filter(_.hasChanged)
      .wireTap(file => providers.track.fileCollected(file = file.path))
}
