package stasis.client.model

import java.time.Instant

import scala.util.Failure
import scala.util.Try

import org.apache.pekko.util.ByteString

import stasis.core.packaging.Crate

sealed trait EntityMetadata {
  def path: String
  def created: Instant
  def updated: Instant

  def asFilesystem: EntityMetadata.Filesystem

  def hasChanged(comparedTo: EntityMetadata): Boolean = (this, comparedTo) match {
    case (a: EntityMetadata.WithContent, b: EntityMetadata.WithContent) => a != b.withCompression(a.compression)
    case _                                                              => this != comparedTo
  }
}

object EntityMetadata {
  sealed trait Filesystem extends EntityMetadata {
    def link: Option[String]
    def isHidden: Boolean
    def owner: String
    def group: String
    def permissions: String

    override def asFilesystem: Filesystem = this
  }

  sealed trait WithContent extends EntityMetadata {
    def size: Long
    def checksum: BigInt
    def crates: Map[String, Crate.Id]
    def compression: String

    def withCrates(crates: Map[String, Crate.Id]): WithContent
    def withCompression(compression: String): WithContent
  }

  final case class File private (
    override val path: String,
    override val link: Option[String],
    override val isHidden: Boolean,
    override val created: Instant,
    override val updated: Instant,
    override val owner: String,
    override val group: String,
    override val permissions: String,
    override val size: Long,
    override val checksum: BigInt,
    override val crates: Map[String, Crate.Id],
    override val compression: String
  ) extends Filesystem
      with WithContent {
    override def withCrates(crates: Map[String, Crate.Id]): WithContent = copy(crates = crates)
    override def withCompression(compression: String): WithContent = copy(compression = compression)
  }

  final case class Directory(
    override val path: String,
    override val link: Option[String],
    override val isHidden: Boolean,
    override val created: Instant,
    override val updated: Instant,
    override val owner: String,
    override val group: String,
    override val permissions: String
  ) extends Filesystem

  final case class Library(
    override val path: String,
    override val created: Instant,
    override val updated: Instant,
    override val size: Long,
    override val checksum: BigInt,
    override val crates: Map[String, Crate.Id],
    override val compression: String,
    attributes: ByteString
  ) extends WithContent {
    override def withCrates(crates: Map[String, Crate.Id]): WithContent = copy(crates = crates)
    override def withCompression(compression: String): WithContent = copy(compression = compression)

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    override def asFilesystem: Filesystem =
      throw new IllegalArgumentException(s"Requested filesystem metadata but library metadata for [$path] found")
  }

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

      case libraryMetadata: Library =>
        val metadata = proto.metadata.LibraryMetadata(
          key = libraryMetadata.path,
          size = libraryMetadata.size,
          created = libraryMetadata.created.getEpochSecond,
          updated = libraryMetadata.updated.getEpochSecond,
          checksum = com.google.protobuf.ByteString.copyFrom(libraryMetadata.checksum.toByteArray),
          crates = libraryMetadata.crates.map(toProtoCrateData),
          compression = libraryMetadata.compression,
          attributes = com.google.protobuf.ByteString.copyFrom(libraryMetadata.attributes.toArray)
        )

        proto.metadata.EntityMetadata(entity = proto.metadata.EntityMetadata.Entity.Library(metadata))
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

      case proto.metadata.EntityMetadata.Entity.Library(libraryMetadata) =>
        Try {
          Library(
            path = libraryMetadata.key,
            size = libraryMetadata.size,
            created = Instant.ofEpochSecond(libraryMetadata.created),
            updated = Instant.ofEpochSecond(libraryMetadata.updated),
            checksum = BigInt(libraryMetadata.checksum.toByteArray),
            crates = libraryMetadata.crates.map(fromProtoCrateData),
            compression = libraryMetadata.compression,
            attributes = ByteString(libraryMetadata.attributes.toByteArray)
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
