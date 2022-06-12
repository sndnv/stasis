package stasis.client.model

import java.nio.file.{Path, Paths}
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import stasis.client.api.clients.ServerApiEndpointClient
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.encryption.{Decoder, Encoder}
import stasis.core.packaging.Crate

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final case class DatasetMetadata(
  contentChanged: Map[Path, EntityMetadata],
  metadataChanged: Map[Path, EntityMetadata],
  filesystem: FilesystemMetadata
) {
  def collect(
    entity: Path,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Future[Option[EntityMetadata]] = {
    val existingMetadata = filesystem.entities.get(entity).map {
      case FilesystemMetadata.EntityState.New | FilesystemMetadata.EntityState.Updated =>
        contentChanged.get(entity).orElse(metadataChanged.get(entity)) match {
          case Some(metadata) =>
            Future.successful(metadata)

          case None =>
            Future.failed(
              new IllegalArgumentException(
                s"Metadata for entity [${entity.toAbsolutePath.toString}] not found"
              )
            )
        }

      case FilesystemMetadata.EntityState.Existing(entry) =>
        for {
          entryMetadata <- api.datasetMetadata(entry)
          entityMetadata <-
            entryMetadata.contentChanged
              .get(entity)
              .orElse(entryMetadata.metadataChanged.get(entity)) match {
              case Some(entityMetadata) =>
                Future.successful(entityMetadata)

              case None =>
                Future.failed(
                  new IllegalArgumentException(
                    s"Expected metadata for entity [${entity.toAbsolutePath.toString}] " +
                      s"but none was found in metadata for entry [${entry.toString}]"
                  )
                )
            }
        } yield {
          entityMetadata
        }
    }

    existingMetadata match {
      case Some(future) => future.map(Some.apply)
      case None         => Future.successful(None)
    }
  }

  def require(
    entity: Path,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Future[EntityMetadata] =
    collect(entity = entity, api = api).flatMap {
      case Some(metadata) =>
        Future.successful(metadata)

      case None =>
        Future.failed(
          new IllegalArgumentException(
            s"Required metadata for entity [${entity.toAbsolutePath.toString}] not found"
          )
        )
    }
}

object DatasetMetadata {
  def empty: DatasetMetadata =
    DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata.empty
    )

  def toByteString(metadata: DatasetMetadata): ByteString = {
    val data = proto.metadata.DatasetMetadata(
      contentChanged = metadata.contentChanged.map { case (entity, metadata) =>
        entity.toAbsolutePath.toString -> EntityMetadata.toProto(metadata)
      },
      metadataChanged = metadata.metadataChanged.map { case (entity, metadata) =>
        entity.toAbsolutePath.toString -> EntityMetadata.toProto(metadata)
      },
      filesystem = Some(FilesystemMetadata.toProto(metadata.filesystem))
    )

    ByteString(data.toByteArray)
  }

  def fromByteString(bytes: ByteString): Try[DatasetMetadata] =
    Try {
      proto.metadata.DatasetMetadata.parseFrom(bytes.toArrayUnsafe())
    }.flatMap { data =>
      for {
        contentChanged <- foldTryMap(
          source = data.contentChanged.map { case (entity, metadata) =>
            Paths.get(entity) -> EntityMetadata.fromProto(metadata)
          }
        )
        metadataChanged <- foldTryMap(
          source = data.metadataChanged.map { case (entity, metadata) =>
            Paths.get(entity) -> EntityMetadata.fromProto(metadata)
          }
        )
        filesystem <- FilesystemMetadata.fromProto(data.filesystem)
      } yield {
        DatasetMetadata(
          contentChanged = contentChanged,
          metadataChanged = metadataChanged,
          filesystem = filesystem
        )
      }
    }

  def encrypt(
    metadataSecret: DeviceMetadataSecret,
    metadata: DatasetMetadata,
    encoder: Encoder
  )(implicit mat: Materializer): Future[ByteString] =
    Source
      .single(metadata)
      .map(DatasetMetadata.toByteString)
      .via(encoder.encrypt(metadataSecret))
      .runFold(ByteString.empty)(_ concat _)

  def decrypt(
    metadataCrate: Crate.Id,
    metadataSecret: DeviceMetadataSecret,
    metadata: Option[Source[ByteString, NotUsed]],
    decoder: Decoder
  )(implicit ec: ExecutionContext, mat: Materializer): Future[DatasetMetadata] =
    metadata match {
      case Some(metadata) =>
        metadata
          .via(decoder.decrypt(metadataSecret))
          .runFold(ByteString.empty)(_ concat _)
          .flatMap { metadata =>
            DatasetMetadata.fromByteString(metadata) match {
              case Success(metadata) => Future.successful(metadata)
              case Failure(e)        => Future.failed(e)
            }
          }

      case None =>
        Future.failed(
          new IllegalArgumentException(s"Cannot decrypt metadata crate [${metadataCrate.toString}]; no data provided")
        )
    }

  private def foldTryMap[K, V](source: Map[K, Try[V]]): Try[Map[K, V]] =
    source.foldLeft(Try(Map.empty[K, V])) { case (tryCollected, (key, tryCurrent)) =>
      tryCollected.flatMap { collected =>
        tryCurrent.map(current => collected + (key -> current))
      }
    }
}
