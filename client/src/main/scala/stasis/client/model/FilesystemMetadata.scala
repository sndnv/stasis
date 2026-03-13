package stasis.client.model

import java.nio.file.FileSystems
import java.util.regex.Pattern

import scala.jdk.CollectionConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.github.sndnv.fsi.Index
import io.github.sndnv.fsi.backends.TrieIndex

import stasis.client.model.FilesystemMetadata.EntityState
import stasis.shared.model.datasets.DatasetEntry

sealed trait FilesystemMetadata {
  def underlying: Index[EntityState]
  def separator: String

  def get(entity: String): Option[EntityState] =
    Option(underlying.get(entity))

  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  def collect[T >: Null](f: PartialFunction[(String, EntityState), T]): Iterable[T] =
    underlying
      .collect[T] { case e: (String, EntityState) => if (f.isDefinedAt(e)) f.apply(e) else null }
      .asScala

  def search(regex: Pattern): Map[String, EntityState] =
    underlying.search(regex).asScala.toMap

  def updated(changes: Iterable[String], latestEntry: DatasetEntry.Id): FilesystemMetadata = {
    val _ = underlying
      .replaceAll {
        case (_, EntityState.New)                => EntityState.Existing(entry = latestEntry)
        case (_, EntityState.Updated)            => EntityState.Existing(entry = latestEntry)
        case (_, existing: EntityState.Existing) => existing
      }
      .putAll(
        changes.asJava,
        (_: String, existing: EntityState) =>
          if (existing != null) EntityState.Updated
          else EntityState.New
      )

    this
  }
}

object FilesystemMetadata {
  private final case class AsTrie(override val underlying: TrieIndex[EntityState]) extends FilesystemMetadata {
    override val separator: String = underlying.getSeparator
  }

  def empty(filesystemSeparator: String): FilesystemMetadata =
    FilesystemMetadata.AsTrie(underlying = TrieIndex.mutable(filesystemSeparator))

  def apply(entities: Map[String, EntityState], filesystemSeparator: String): FilesystemMetadata =
    FilesystemMetadata.AsTrie(underlying = TrieIndex.mutable(filesystemSeparator).putAll(entities.asJava))

  def apply(changes: Iterable[String], filesystemSeparator: String): FilesystemMetadata =
    FilesystemMetadata.AsTrie(
      underlying = TrieIndex
        .mutable(filesystemSeparator)
        .putAll(changes.asJava, (_, _) => EntityState.New)
    )

  def toProto(filesystem: FilesystemMetadata): proto.metadata.FilesystemMetadata = {
    val result = scala.collection.mutable.Map[String, proto.metadata.EntityState]()

    filesystem.underlying.forEach { case (path, state) =>
      val _ = result.put(path, EntityState.toProto(state))
      kotlin.Unit.INSTANCE
    }

    proto.metadata.FilesystemMetadata(
      entities = result.toMap
    )
  }

  def fromProto(filesystem: Option[proto.metadata.FilesystemMetadata]): Try[FilesystemMetadata] =
    filesystem match {
      case Some(filesystem) =>
        val entities = foldTryMap(
          filesystem.entities.map { case (entity, state) =>
            entity -> EntityState.fromProto(state)
          }
        )

        val separator = Option(filesystem.separator)
          .map(_.trim)
          .filter(_.nonEmpty)
          .getOrElse(DefaultFilesystemSeparator)

        entities.map(FilesystemMetadata.apply(_, separator))

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

  lazy val DefaultFilesystemSeparator: String = FileSystems.getDefault.getSeparator
}
