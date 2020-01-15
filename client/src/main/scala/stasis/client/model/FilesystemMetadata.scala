package stasis.client.model

import java.nio.file.{Path, Paths}

import stasis.client.model.FilesystemMetadata.FileState
import stasis.shared.model.datasets.DatasetEntry

import scala.util.{Failure, Success, Try}

final case class FilesystemMetadata(
  files: Map[Path, FileState]
) {
  def updated(changes: Iterable[Path], latestEntry: DatasetEntry.Id): FilesystemMetadata = {
    val newAndUpdated = changes.map {
      case file if files.contains(file) => file -> (FilesystemMetadata.FileState.Updated: FilesystemMetadata.FileState)
      case file                         => file -> (FilesystemMetadata.FileState.New: FilesystemMetadata.FileState)
    }

    val existing = files.mapValues {
      case FilesystemMetadata.FileState.New                => FilesystemMetadata.FileState.Existing(entry = latestEntry)
      case FilesystemMetadata.FileState.Updated            => FilesystemMetadata.FileState.Existing(entry = latestEntry)
      case existing: FilesystemMetadata.FileState.Existing => existing
    }

    FilesystemMetadata(files = existing ++ newAndUpdated)
  }
}

object FilesystemMetadata {
  def empty: FilesystemMetadata = FilesystemMetadata(files = Map.empty)

  def apply(changes: Iterable[Path]): FilesystemMetadata =
    FilesystemMetadata(
      files = changes.map(file => file -> FileState.New).toMap
    )

  def toProto(filesystem: FilesystemMetadata): proto.metadata.FilesystemMetadata =
    proto.metadata.FilesystemMetadata(
      files = filesystem.files.map {
        case (file, state) =>
          file.toAbsolutePath.toString -> FileState.toProto(state)
      }
    )

  def fromProto(filesystem: Option[proto.metadata.FilesystemMetadata]): Try[FilesystemMetadata] =
    filesystem match {
      case Some(filesystem) =>
        val files = foldTryMap(
          filesystem.files.map {
            case (file, state) => Paths.get(file) -> FileState.fromProto(state)
          }
        )

        files.map(FilesystemMetadata.apply)

      case None =>
        Failure(new IllegalArgumentException("No filesystem metadata provided"))
    }

  sealed trait FileState

  object FileState {
    case object New extends FileState
    final case class Existing(entry: DatasetEntry.Id) extends FileState
    case object Updated extends FileState

    def toProto(state: FileState): proto.metadata.FileState =
      proto.metadata.FileState(
        state match {
          case FileState.New =>
            proto.metadata.FileState.State.PresentNew(proto.metadata.FileState.PresentNew())

          case FileState.Existing(entry) =>
            proto.metadata.FileState.State.PresentExisting(
              proto.metadata.FileState.PresentExisting(
                entry = Some(
                  proto.metadata.Uuid(
                    mostSignificantBits = entry.getMostSignificantBits,
                    leastSignificantBits = entry.getLeastSignificantBits
                  )
                )
              )
            )

          case FileState.Updated =>
            proto.metadata.FileState.State.PresentUpdated(proto.metadata.FileState.PresentUpdated())
        }
      )

    def fromProto(state: proto.metadata.FileState): Try[FileState] =
      state.state match {
        case _: proto.metadata.FileState.State.PresentNew =>
          Success(FileState.New)

        case existing: proto.metadata.FileState.State.PresentExisting =>
          val tryEntry = existing.value.entry match {
            case Some(entryId) =>
              Success(
                new java.util.UUID(
                  entryId.mostSignificantBits,
                  entryId.leastSignificantBits
                )
              )

            case None =>
              Failure(new IllegalArgumentException("No entry ID found for existing file"))
          }

          tryEntry.map(FileState.Existing)

        case _: proto.metadata.FileState.State.PresentUpdated =>
          Success(FileState.Updated)

        case proto.metadata.FileState.State.Empty =>
          Failure(new IllegalArgumentException("Unexpected empty file state encountered"))
      }
  }

  private def foldTryMap[K, V](source: Map[K, Try[V]]): Try[Map[K, V]] =
    source.foldLeft(Try(Map.empty[K, V])) {
      case (tryCollected, (key, tryCurrent)) =>
        tryCollected.flatMap { collected =>
          tryCurrent.map(current => collected + (key -> current))
        }
    }
}
