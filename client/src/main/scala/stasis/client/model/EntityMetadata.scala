package stasis.client.model

import java.time.Instant

import scala.util.Failure
import scala.util.Try

import stasis.core.packaging.Crate

sealed trait EntityMetadata {
  def path: String
  def link: Option[String]
  def isHidden: Boolean
  def created: Instant
  def updated: Instant
  def owner: String
  def group: String
  def permissions: String

  def hasChanged(comparedTo: EntityMetadata): Boolean = (this, comparedTo) match {
    case (a: EntityMetadata.File, b: EntityMetadata.File) => a != b.copy(compression = a.compression)
    case _                                                => this != comparedTo
  }
}

object EntityMetadata {
  final case class File private (
    override val path: String,
    override val link: Option[String],
    override val isHidden: Boolean,
    override val created: Instant,
    override val updated: Instant,
    override val owner: String,
    override val group: String,
    override val permissions: String,
    size: Long,
    checksum: BigInt,
    crates: Map[String, Crate.Id],
    compression: String
  ) extends EntityMetadata

  final case class Directory(
    override val path: String,
    override val link: Option[String],
    override val isHidden: Boolean,
    override val created: Instant,
    override val updated: Instant,
    override val owner: String,
    override val group: String,
    override val permissions: String
  ) extends EntityMetadata

  def toProto(entityMetadata: EntityMetadata): proto.metadata.EntityMetadata =
    entityMetadata match {
      case fileMetadata: File =>
        val metadata = proto.metadata.FileMetadata(
          path = fileMetadata.path,
          size = fileMetadata.size,
          link = fileMetadata.link.getOrElse(""),
          isHidden = fileMetadata.isHidden,
          created = fileMetadata.created.getEpochSecond,
          updated = fileMetadata.updated.getEpochSecond,
          owner = fileMetadata.owner,
          group = fileMetadata.group,
          permissions = fileMetadata.permissions,
          checksum = com.google.protobuf.ByteString.copyFrom(fileMetadata.checksum.toByteArray),
          crates = fileMetadata.crates.map(toProtoCrateData),
          compression = fileMetadata.compression
        )

        proto.metadata.EntityMetadata(entity = proto.metadata.EntityMetadata.Entity.File(metadata))

      case directoryMetadata: Directory =>
        val metadata = proto.metadata.DirectoryMetadata(
          path = directoryMetadata.path,
          link = directoryMetadata.link.getOrElse(""),
          isHidden = directoryMetadata.isHidden,
          created = directoryMetadata.created.getEpochSecond,
          updated = directoryMetadata.updated.getEpochSecond,
          owner = directoryMetadata.owner,
          group = directoryMetadata.group,
          permissions = directoryMetadata.permissions
        )

        proto.metadata.EntityMetadata(entity = proto.metadata.EntityMetadata.Entity.Directory(metadata))
    }

  def fromProto(entityMetadata: proto.metadata.EntityMetadata): Try[EntityMetadata] =
    entityMetadata.entity match {
      case proto.metadata.EntityMetadata.Entity.File(fileMetadata) =>
        Try {
          File(
            path = fileMetadata.path,
            size = fileMetadata.size,
            link = if (fileMetadata.link.nonEmpty) Some(fileMetadata.link) else None,
            isHidden = fileMetadata.isHidden,
            created = Instant.ofEpochSecond(fileMetadata.created),
            updated = Instant.ofEpochSecond(fileMetadata.updated),
            owner = fileMetadata.owner,
            group = fileMetadata.group,
            permissions = fileMetadata.permissions,
            checksum = BigInt(fileMetadata.checksum.toByteArray),
            crates = fileMetadata.crates.map(fromProtoCrateData),
            compression = fileMetadata.compression
          )
        }

      case proto.metadata.EntityMetadata.Entity.Directory(directoryMetadata) =>
        Try {
          Directory(
            path = directoryMetadata.path,
            link = if (directoryMetadata.link.nonEmpty) Some(directoryMetadata.link) else None,
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

  private def fromProtoCrateData(crateData: (String, proto.metadata.Uuid)): (String, java.util.UUID) =
    crateData match {
      case (path, uuid) =>
        (
          path,
          new java.util.UUID(
            uuid.mostSignificantBits,
            uuid.leastSignificantBits
          )
        )
    }

  private def toProtoCrateData(crateData: (String, java.util.UUID)): (String, proto.metadata.Uuid) =
    crateData match {
      case (path, uuid) =>
        (
          path,
          proto.metadata.Uuid(
            mostSignificantBits = uuid.getMostSignificantBits,
            leastSignificantBits = uuid.getLeastSignificantBits
          )
        )
    }
}
