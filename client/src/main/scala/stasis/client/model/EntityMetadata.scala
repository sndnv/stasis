package stasis.client.model

import java.nio.file.{Path, Paths}
import java.time.Instant

import stasis.core.packaging.Crate

import scala.util.{Failure, Success, Try}

sealed trait EntityMetadata {
  def path: Path
  def link: Option[Path]
  def isHidden: Boolean
  def created: Instant
  def updated: Instant
  def owner: String
  def group: String
  def permissions: String

  def collectCrate: Try[Crate.Id]
}

object EntityMetadata {
  final case class File(
    override val path: Path,
    override val link: Option[Path],
    override val isHidden: Boolean,
    override val created: Instant,
    override val updated: Instant,
    override val owner: String,
    override val group: String,
    override val permissions: String,
    size: Long,
    checksum: BigInt,
    crate: Crate.Id
  ) extends EntityMetadata {
    override def collectCrate: Try[Crate.Id] =
      Success(crate)
  }

  final case class Directory(
    override val path: Path,
    override val link: Option[Path],
    override val isHidden: Boolean,
    override val created: Instant,
    override val updated: Instant,
    override val owner: String,
    override val group: String,
    override val permissions: String
  ) extends EntityMetadata {
    override def collectCrate: Try[Crate.Id] =
      Failure(new IllegalStateException(s"Crate not available for directory entity [$path]"))
  }

  def toProto(entityMetadata: EntityMetadata): proto.metadata.EntityMetadata =
    entityMetadata match {
      case fileMetadata: File =>
        val metadata = proto.metadata.FileMetadata(
          path = fileMetadata.path.toAbsolutePath.toString,
          size = fileMetadata.size,
          link = fileMetadata.link.fold("")(_.toAbsolutePath.toString),
          isHidden = fileMetadata.isHidden,
          created = fileMetadata.created.getEpochSecond,
          updated = fileMetadata.updated.getEpochSecond,
          owner = fileMetadata.owner,
          group = fileMetadata.group,
          permissions = fileMetadata.permissions,
          checksum = com.google.protobuf.ByteString.copyFrom(fileMetadata.checksum.toByteArray),
          crate = Some(
            proto.metadata.Uuid(
              mostSignificantBits = fileMetadata.crate.getMostSignificantBits,
              leastSignificantBits = fileMetadata.crate.getLeastSignificantBits
            )
          )
        )

        proto.metadata.EntityMetadata(entity = proto.metadata.EntityMetadata.Entity.File(metadata))

      case directoryMetadata: Directory =>
        val metadata = proto.metadata.DirectoryMetadata(
          path = directoryMetadata.path.toAbsolutePath.toString,
          link = directoryMetadata.link.fold("")(_.toAbsolutePath.toString),
          isHidden = directoryMetadata.isHidden,
          created = directoryMetadata.created.getEpochSecond,
          updated = directoryMetadata.updated.getEpochSecond,
          owner = directoryMetadata.owner,
          group = directoryMetadata.group,
          permissions = directoryMetadata.permissions,
        )

        proto.metadata.EntityMetadata(entity = proto.metadata.EntityMetadata.Entity.Directory(metadata))
    }

  def fromProto(entityMetadata: proto.metadata.EntityMetadata): Try[EntityMetadata] =
    entityMetadata.entity match {
      case proto.metadata.EntityMetadata.Entity.File(fileMetadata) =>
        val tryCrate = fileMetadata.crate match {
          case Some(crateId) =>
            Success(
              new java.util.UUID(
                crateId.mostSignificantBits,
                crateId.leastSignificantBits
              )
            )

          case None =>
            Failure(new IllegalArgumentException(s"Metadata for file [${fileMetadata.path}] missing crate ID"))
        }

        tryCrate.flatMap { crate =>
          Try {
            File(
              path = Paths.get(fileMetadata.path),
              size = fileMetadata.size,
              link = if (fileMetadata.link.nonEmpty) Some(Paths.get(fileMetadata.link)) else None,
              isHidden = fileMetadata.isHidden,
              created = Instant.ofEpochSecond(fileMetadata.created),
              updated = Instant.ofEpochSecond(fileMetadata.updated),
              owner = fileMetadata.owner,
              group = fileMetadata.group,
              permissions = fileMetadata.permissions,
              checksum = BigInt(fileMetadata.checksum.toByteArray),
              crate = crate
            )
          }
        }

      case proto.metadata.EntityMetadata.Entity.Directory(directoryMetadata) =>
        Try {
          Directory(
            path = Paths.get(directoryMetadata.path),
            link = if (directoryMetadata.link.nonEmpty) Some(Paths.get(directoryMetadata.link)) else None,
            isHidden = directoryMetadata.isHidden,
            created = Instant.ofEpochSecond(directoryMetadata.created),
            updated = Instant.ofEpochSecond(directoryMetadata.updated),
            owner = directoryMetadata.owner,
            group = directoryMetadata.group,
            permissions = directoryMetadata.permissions
          )
        }

      case proto.metadata.EntityMetadata.Entity.Empty =>
        Failure(new IllegalArgumentException("Expected entity in metadata but none was found"))
    }
}
