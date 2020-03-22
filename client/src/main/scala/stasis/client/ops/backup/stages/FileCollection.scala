package stasis.client.ops.backup.stages

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.collection.BackupCollector
import stasis.client.model.SourceFile
import stasis.client.ops.backup.Providers
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.ops.Operation

trait FileCollection {
  protected def targetDataset: DatasetDefinition
  protected def providers: Providers
  protected def collector: BackupCollector

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
