package stasis.client.ops.backup.stages

import java.nio.file.Path

import akka.NotUsed
import akka.stream.scaladsl.Flow
import stasis.client.model.{DatasetMetadata, EntityMetadata, FilesystemMetadata}
import stasis.client.ops.backup.Providers
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.ops.Operation

trait MetadataCollection {
  protected def targetDataset: DatasetDefinition
  protected def latestEntry: Option[DatasetEntry]
  protected def latestMetadata: Option[DatasetMetadata]
  protected def providers: Providers

  def metadataCollection(
    implicit operation: Operation.Id
  ): Flow[Either[EntityMetadata, EntityMetadata], DatasetMetadata, NotUsed] =
    Flow[Either[EntityMetadata, EntityMetadata]]
      .fold((Map.empty[Path, EntityMetadata], Map.empty[Path, EntityMetadata])) {
        case ((contentChanged, metadataChanged), currentMetadata) =>
          currentMetadata match {
            case Left(metadata)  => (contentChanged + (metadata.path -> metadata), metadataChanged)
            case Right(metadata) => (contentChanged, metadataChanged + (metadata.path -> metadata))
          }
      }
      .map {
        case (contentChanged, metadataChanged) =>
          DatasetMetadata(
            contentChanged = contentChanged,
            metadataChanged = metadataChanged,
            filesystem = (latestMetadata, latestEntry) match {
              case (Some(metadata), Some(entry)) =>
                metadata.filesystem
                  .updated(
                    changes = contentChanged.keys ++ metadataChanged.keys,
                    latestEntry = entry.id
                  )

              case _ =>
                FilesystemMetadata(contentChanged.keys ++ metadataChanged.keys)
            }
          )
      }
      .wireTap(_ => providers.track.metadataCollected())
}
