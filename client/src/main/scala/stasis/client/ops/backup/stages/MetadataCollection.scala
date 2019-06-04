package stasis.client.ops.backup.stages

import akka.NotUsed
import akka.stream.scaladsl.Flow
import stasis.client.model.{DatasetMetadata, FileMetadata}
import stasis.shared.model.datasets.DatasetDefinition

trait MetadataCollection {
  protected def targetDataset: DatasetDefinition

  def metadataCollection: Flow[Either[FileMetadata, FileMetadata], DatasetMetadata, NotUsed] =
    Flow[Either[FileMetadata, FileMetadata]]
      .fold((Seq.empty[FileMetadata], Seq.empty[FileMetadata])) {
        case ((contentChanged, metadataChanged), currentMetadata) =>
          currentMetadata match {
            case Left(metadata)  => (contentChanged :+ metadata, metadataChanged)
            case Right(metadata) => (contentChanged, metadataChanged :+ metadata)
          }
      }
      .map {
        case (contentChanged, metadataChanged) =>
          DatasetMetadata(contentChanged, metadataChanged)
      }
      .log(
        name = "Metadata Collection",
        extract = metadata => s"Metadata generated for dataset [${targetDataset.id}]: [$metadata]"
      )
}
