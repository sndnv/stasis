package stasis.client.model

import java.nio.file.Path
import java.nio.file.Paths

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

import stasis.client.api.clients.Clients
import stasis.client.compression.Gzip
import stasis.client.compression.{Decoder => CompressionDecoder}
import stasis.client.compression.{Encoder => CompressionEncoder}
import stasis.client.encryption.secrets.DeviceMetadataSecret
import stasis.client.encryption.{Decoder => EncryptionDecoder}
import stasis.client.encryption.{Encoder => EncryptionEncoder}
import stasis.core.packaging.Crate

final case class DatasetMetadata(
  contentChanged: Map[Path, EntityMetadata],
  metadataChanged: Map[Path, EntityMetadata],
  filesystem: FilesystemMetadata
) {
  def collect(
    entity: Path,
    clients: Clients
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
          entryMetadata <- clients.api.datasetMetadata(entry)
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
    clients: Clients
  )(implicit ec: ExecutionContext): Future[EntityMetadata] =
    collect(entity = entity, clients = clients).flatMap {
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
  private final val DefaultCompression: CompressionEncoder with CompressionDecoder = Gzip

  def empty: DatasetMetadata =
    DatasetMetadata(
      contentChanged = Map.empty,
      metadataChanged = Map.empty,
      filesystem = FilesystemMetadata.empty
    )

  def toByteString(metadata: DatasetMetadata)(implicit mat: Materializer): Future[ByteString] = {
    val data = proto.metadata.DatasetMetadata(
      contentChanged = metadata.contentChanged.map { case (entity, metadata) =>
        entity.toAbsolutePath.toString -> EntityMetadata.toProto(metadata)
      },
      metadataChanged = metadata.metadataChanged.map { case (entity, metadata) =>
        entity.toAbsolutePath.toString -> EntityMetadata.toProto(metadata)
      },
      filesystem = Some(FilesystemMetadata.toProto(metadata.filesystem))
    )

    Source
      .single(ByteString.fromArrayUnsafe(data.toByteArray))
      .via(DefaultCompression.compress)
      .runFold(ByteString.empty)(_ concat _)
  }

  def fromByteString(bytes: ByteString)(implicit mat: Materializer): Future[DatasetMetadata] = {
    import mat.executionContext

    Source
      .single(bytes)
      .via(DefaultCompression.decompress)
      .runFold(ByteString.empty)(_ concat _)
      .map(data => proto.metadata.DatasetMetadata.parseFrom(data.toArrayUnsafe()))
      .flatMap { data =>
        val result = for {
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

        Future.fromTry(result)
      }
  }

  def encrypt(
    metadataSecret: DeviceMetadataSecret,
    metadata: DatasetMetadata,
    encoder: EncryptionEncoder
  )(implicit mat: Materializer): Future[ByteString] =
    Source
      .single(metadata)
      .mapAsync(parallelism = 1)(DatasetMetadata.toByteString)
      .via(encoder.encrypt(metadataSecret))
      .runFold(ByteString.empty)(_ concat _)

  def decrypt(
    metadataCrate: Crate.Id,
    metadataSecret: DeviceMetadataSecret,
    metadata: Option[Source[ByteString, NotUsed]],
    decoder: EncryptionDecoder
  )(implicit ec: ExecutionContext, mat: Materializer): Future[DatasetMetadata] =
    metadata match {
      case Some(metadata) =>
        metadata
          .via(decoder.decrypt(metadataSecret))
          .runFold(ByteString.empty)(_ concat _)
          .flatMap(DatasetMetadata.fromByteString)

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
