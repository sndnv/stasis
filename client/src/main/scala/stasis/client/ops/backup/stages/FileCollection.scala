package stasis.client.ops.backup.stages

import akka.NotUsed
import akka.stream.scaladsl.Source
import stasis.client.model.SourceFile
import stasis.client.ops.backup.Providers
import stasis.shared.model.datasets.DatasetDefinition

trait FileCollection {
  protected def targetDataset: DatasetDefinition
  protected def providers: Providers

  def fileCollection: Source[SourceFile, NotUsed] =
    providers.collector
      .collect()
      .log(
        name = "File Collection",
        extract = { file =>
          val metadata = if (file.hasChanged) "changed" else "not changed"
          val content = if (file.hasContentChanged) "changed" else "not changed"
          s"[${file.path}] - Dataset: [${targetDataset.id}]; Metadata: [$metadata]; Content: [$content]"
        }
      )
      .filter(_.hasChanged)
}
