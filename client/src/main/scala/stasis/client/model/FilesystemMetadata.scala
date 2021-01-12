package stasis.client.model

import java.nio.file.{Path, Paths}

import stasis.client.model.FilesystemMetadata.EntityState
import stasis.shared.model.datasets.DatasetEntry

import scala.util.{Failure, Success, Try}

final case class FilesystemMetadata(
  entities: Map[Path, EntityState]
) {
  def updated(changes: Iterable[Path], latestEntry: DatasetEntry.Id): FilesystemMetadata = {
    val newAndUpdated = changes.map {
      case entity if entities.contains(entity) => entity -> (EntityState.Updated: EntityState)
      case entity                              => entity -> (EntityState.New: EntityState)
    }

    val existing = entities.view.mapValues {
      case EntityState.New                => EntityState.Existing(entry = latestEntry)
      case EntityState.Updated            => EntityState.Existing(entry = latestEntry)
      case existing: EntityState.Existing => existing
    }.toMap

    FilesystemMetadata(entities = existing ++ newAndUpdated)
  }
}

object FilesystemMetadata {
  def empty: FilesystemMetadata = FilesystemMetadata(entities = Map.empty)

  def apply(changes: Iterable[Path]): FilesystemMetadata =
    FilesystemMetadata(
      entities = changes.map(entity => entity -> EntityState.New).toMap
    )

  def toProto(filesystem: FilesystemMetadata): proto.metadata.FilesystemMetadata =
    proto.metadata.FilesystemMetadata(
      entities = filesystem.entities.map { case (entity, state) =>
        entity.toAbsolutePath.toString -> EntityState.toProto(state)
      }
    )

  def fromProto(filesystem: Option[proto.metadata.FilesystemMetadata]): Try[FilesystemMetadata] =
    filesystem match {
      case Some(filesystem) =>
        val entities = foldTryMap(
          filesystem.entities.map { case (entity, state) =>
            Paths.get(entity) -> EntityState.fromProto(state)
          }
        )

        entities.map(FilesystemMetadata.apply)

      case None =>
        Failure(new IllegalArgumentException("No filesystem metadata provided"))
    }

  sealed trait EntityState

  object EntityState {
    case object New extends EntityState
    final case class Existing(entry: DatasetEntry.Id) extends EntityState
    case object Updated extends EntityState

    def toProto(state: EntityState): proto.metadata.EntityState =
      proto.metadata.EntityState(
        state match {
          case EntityState.New =>
            proto.metadata.EntityState.State.PresentNew(proto.metadata.EntityState.PresentNew())

          case EntityState.Existing(entry) =>
            proto.metadata.EntityState.State.PresentExisting(
              proto.metadata.EntityState.PresentExisting(
                entry = Some(
                  proto.metadata.Uuid(
                    mostSignificantBits = entry.getMostSignificantBits,
                    leastSignificantBits = entry.getLeastSignificantBits
                  )
                )
              )
            )

          case EntityState.Updated =>
            proto.metadata.EntityState.State.PresentUpdated(proto.metadata.EntityState.PresentUpdated())
        }
      )

    def fromProto(state: proto.metadata.EntityState): Try[EntityState] =
      state.state match {
        case _: proto.metadata.EntityState.State.PresentNew =>
          Success(EntityState.New)

        case existing: proto.metadata.EntityState.State.PresentExisting =>
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

          tryEntry.map(EntityState.Existing)

        case _: proto.metadata.EntityState.State.PresentUpdated =>
          Success(EntityState.Updated)

        case proto.metadata.EntityState.State.Empty =>
          Failure(new IllegalArgumentException("Unexpected empty file state encountered"))
      }
  }

  private def foldTryMap[K, V](source: Map[K, Try[V]]): Try[Map[K, V]] =
    source.foldLeft(Try(Map.empty[K, V])) { case (tryCollected, (key, tryCurrent)) =>
      tryCollected.flatMap { collected =>
        tryCurrent.map(current => collected + (key -> current))
      }
    }
}
