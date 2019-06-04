package stasis.client.model

import java.nio.file.{Path, Paths}
import java.time.Instant

import stasis.core.packaging.Crate

import scala.util.{Failure, Success, Try}

final case class FileMetadata(
  path: Path,
  size: Long,
  link: Option[Path],
  isHidden: Boolean,
  created: Instant,
  updated: Instant,
  owner: String,
  group: String,
  permissions: String,
  checksum: BigInt,
  crate: Crate.Id
)

object FileMetadata {
  def toProto(fileMetadata: FileMetadata): proto.metadata.FileMetadata =
    proto.metadata.FileMetadata(
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
        proto.metadata.CrateId(
          mostSignificantBits = fileMetadata.crate.getMostSignificantBits,
          leastSignificantBits = fileMetadata.crate.getLeastSignificantBits
        )
      )
    )

  def fromProto(fileMetadata: proto.metadata.FileMetadata): Try[FileMetadata] = {
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
        FileMetadata(
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
  }
}
