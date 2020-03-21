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
  contentChanged: Map[Path, FileMetadata],
  metadataChanged: Map[Path, FileMetadata],
  filesystem: FilesystemMetadata
) {
  def collect(
    file: Path,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Future[Option[FileMetadata]] = {
    val existingMetadata = filesystem.files.get(file).map {
      case FilesystemMetadata.FileState.New | FilesystemMetadata.FileState.Updated =>
        contentChanged.get(file).orElse(metadataChanged.get(file)) match {
          case Some(metadata) =>
            Future.successful(metadata)

          case None =>
            Future.failed(
              new IllegalArgumentException(
                s"Metadata for file [${file.toAbsolutePath}] not found"
              )
            )
        }

      case FilesystemMetadata.FileState.Existing(entry) =>
        for {
          entryMetadata <- api.datasetMetadata(entry)
          fileMetadata <- entryMetadata.contentChanged
            .get(file)
            .orElse(entryMetadata.metadataChanged.get(file)) match {
            case Some(fileMetadata) =>
              Future.successful(fileMetadata)

            case None =>
              Future.failed(
                new IllegalArgumentException(
                  s"Expected metadata for file [${file.toAbsolutePath}] but none was found in metadata for entry [$entry]"
                )
              )
          }
        } yield {
          fileMetadata
        }
    }

    existingMetadata match {
      case Some(future) => future.map(Some.apply)
      case None         => Future.successful(None)
    }
  }

  def require(
    file: Path,
    api: ServerApiEndpointClient
  )(implicit ec: ExecutionContext): Future[FileMetadata] =
    collect(file = file, api = api).flatMap {
      case Some(metadata) =>
        Future.successful(metadata)

      case None =>
        Future.failed(
          new IllegalArgumentException(
            s"Required metadata for file [${file.toAbsolutePath}] not found"
          )
        )
    }
}

object DatasetMetadata {
  def empty: DatasetMetadata = DatasetMetadata(
    contentChanged = Map.empty,
    metadataChanged = Map.empty,
    filesystem = FilesystemMetadata.empty
  )

  def toByteString(metadata: DatasetMetadata): akka.util.ByteString = {
    val data = proto.metadata.DatasetMetadata(
      contentChanged = metadata.contentChanged.map {
        case (file, metadata) => file.toAbsolutePath.toString -> FileMetadata.toProto(metadata)
      },
      metadataChanged = metadata.metadataChanged.map {
        case (file, metadata) => file.toAbsolutePath.toString -> FileMetadata.toProto(metadata)
      },
      filesystem = Some(FilesystemMetadata.toProto(metadata.filesystem))
    )

    akka.util.ByteString(data.toByteArray)
  }

  def fromByteString(bytes: akka.util.ByteString): Try[DatasetMetadata] =
    Try {
      proto.metadata.DatasetMetadata.parseFrom(bytes.toArray)
    }.flatMap { data =>
      for {
        contentChanged <- foldTryMap(
          source = data.contentChanged.map {
            case (file, metadata) => Paths.get(file) -> FileMetadata.fromProto(metadata)
          }
        )
        metadataChanged <- foldTryMap(
          source = data.metadataChanged.map {
            case (file, metadata) => Paths.get(file) -> FileMetadata.fromProto(metadata)
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
    metadataCrate: Crate.Id,
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
          new IllegalArgumentException(s"Cannot decrypt metadata crate [$metadataCrate]; no data provided")
        )
    }

  private def foldTryMap[K, V](source: Map[K, Try[V]]): Try[Map[K, V]] =
    source.foldLeft(Try(Map.empty[K, V])) {
      case (tryCollected, (key, tryCurrent)) =>
        tryCollected.flatMap { collected =>
          tryCurrent.map(current => collected + (key -> current))
        }
    }
}
