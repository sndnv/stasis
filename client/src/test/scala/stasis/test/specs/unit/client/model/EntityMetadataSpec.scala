package stasis.test.specs.unit.client.model

import stasis.client.model.{proto, EntityMetadata}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.{Failure, Success}

class EntityMetadataSpec extends UnitSpec {
  "A EntityMetadata" should "support retrieving crate IDs" in {
    Fixtures.Metadata.FileOneMetadata.collectCrate match {
      case Success(crate) => crate should be(Fixtures.Metadata.FileOneMetadata.crate)
      case Failure(e)     => fail(e)
    }

    Fixtures.Metadata.DirectoryOneMetadata.collectCrate match {
      case Success(crate)                    => fail(s"Unexpected successful result received: [$crate]")
      case Failure(e: IllegalStateException) => e.getMessage should startWith("Crate not available for directory entity")
      case Failure(e)                        => fail(e)
    }
  }

  it should "be serializable to protobuf data" in {
    EntityMetadata.toProto(Fixtures.Metadata.FileOneMetadata) should be(fileOneMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.DirectoryOneMetadata) should be(directoryOneMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.FileTwoMetadata) should be(fileTwoMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.DirectoryTwoMetadata) should be(directoryTwoMetadataProto)
  }

  it should "be deserializable from valid protobuf data" in {
    EntityMetadata.fromProto(fileOneMetadataProto) should be(Success(Fixtures.Metadata.FileOneMetadata))
    EntityMetadata.fromProto(directoryOneMetadataProto) should be(Success(Fixtures.Metadata.DirectoryOneMetadata))
    EntityMetadata.fromProto(fileTwoMetadataProto) should be(Success(Fixtures.Metadata.FileTwoMetadata))
    EntityMetadata.fromProto(directoryTwoMetadataProto) should be(Success(Fixtures.Metadata.DirectoryTwoMetadata))
  }

  it should "fail to be deserialized when empty entity is provided" in {
    EntityMetadata.fromProto(entityMetadata = emtpyMetadataProto) match {
      case Success(metadata) => fail(s"Unexpected successful result received: [$metadata]")
      case Failure(e)        => e shouldBe a[IllegalArgumentException]
    }
  }

  it should "fail to be deserialized from invalid protobuf data" in {
    EntityMetadata.fromProto(entityMetadata = invalidFileMetadataProto) match {
      case Success(metadata) => fail(s"Unexpected successful result received: [$metadata]")
      case Failure(e)        => e shouldBe a[IllegalArgumentException]
    }
  }

  private val actualFileOneMetadata = proto.metadata.FileMetadata(
    path = Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath.toString,
    size = Fixtures.Metadata.FileOneMetadata.size,
    link = Fixtures.Metadata.FileOneMetadata.link.fold("")(_.toAbsolutePath.toString),
    isHidden = Fixtures.Metadata.FileOneMetadata.isHidden,
    created = Fixtures.Metadata.FileOneMetadata.created.getEpochSecond,
    updated = Fixtures.Metadata.FileOneMetadata.updated.getEpochSecond,
    owner = Fixtures.Metadata.FileOneMetadata.owner,
    group = Fixtures.Metadata.FileOneMetadata.group,
    permissions = Fixtures.Metadata.FileOneMetadata.permissions,
    checksum = com.google.protobuf.ByteString.copyFrom(Fixtures.Metadata.FileOneMetadata.checksum.toByteArray),
    crate = Some(
      proto.metadata.Uuid(
        mostSignificantBits = Fixtures.Metadata.FileOneMetadata.crate.getMostSignificantBits,
        leastSignificantBits = Fixtures.Metadata.FileOneMetadata.crate.getLeastSignificantBits
      )
    )
  )

  private val actualFileTwoMetadata = proto.metadata.FileMetadata(
    path = Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath.toString,
    size = Fixtures.Metadata.FileTwoMetadata.size,
    link = Fixtures.Metadata.FileTwoMetadata.link.fold("")(_.toAbsolutePath.toString),
    isHidden = Fixtures.Metadata.FileTwoMetadata.isHidden,
    created = Fixtures.Metadata.FileTwoMetadata.created.getEpochSecond,
    updated = Fixtures.Metadata.FileTwoMetadata.updated.getEpochSecond,
    owner = Fixtures.Metadata.FileTwoMetadata.owner,
    group = Fixtures.Metadata.FileTwoMetadata.group,
    permissions = Fixtures.Metadata.FileTwoMetadata.permissions,
    checksum = com.google.protobuf.ByteString.copyFrom(Fixtures.Metadata.FileTwoMetadata.checksum.toByteArray),
    crate = Some(
      proto.metadata.Uuid(
        mostSignificantBits = Fixtures.Metadata.FileTwoMetadata.crate.getMostSignificantBits,
        leastSignificantBits = Fixtures.Metadata.FileTwoMetadata.crate.getLeastSignificantBits
      )
    )
  )

  private val fileOneMetadataProto = proto.metadata.EntityMetadata(
    entity = proto.metadata.EntityMetadata.Entity.File(value = actualFileOneMetadata)
  )

  private val fileTwoMetadataProto = proto.metadata.EntityMetadata(
    entity = proto.metadata.EntityMetadata.Entity.File(value = actualFileTwoMetadata)
  )

  private val actualDirectoryOneMetadata = proto.metadata.DirectoryMetadata(
    path = Fixtures.Metadata.DirectoryOneMetadata.path.toAbsolutePath.toString,
    link = Fixtures.Metadata.DirectoryOneMetadata.link.fold("")(_.toAbsolutePath.toString),
    isHidden = Fixtures.Metadata.DirectoryOneMetadata.isHidden,
    created = Fixtures.Metadata.DirectoryOneMetadata.created.getEpochSecond,
    updated = Fixtures.Metadata.DirectoryOneMetadata.updated.getEpochSecond,
    owner = Fixtures.Metadata.DirectoryOneMetadata.owner,
    group = Fixtures.Metadata.DirectoryOneMetadata.group,
    permissions = Fixtures.Metadata.DirectoryOneMetadata.permissions
  )

  private val actualDirectoryTwoMetadata = proto.metadata.DirectoryMetadata(
    path = Fixtures.Metadata.DirectoryTwoMetadata.path.toAbsolutePath.toString,
    link = Fixtures.Metadata.DirectoryTwoMetadata.link.fold("")(_.toAbsolutePath.toString),
    isHidden = Fixtures.Metadata.DirectoryTwoMetadata.isHidden,
    created = Fixtures.Metadata.DirectoryTwoMetadata.created.getEpochSecond,
    updated = Fixtures.Metadata.DirectoryTwoMetadata.updated.getEpochSecond,
    owner = Fixtures.Metadata.DirectoryTwoMetadata.owner,
    group = Fixtures.Metadata.DirectoryTwoMetadata.group,
    permissions = Fixtures.Metadata.DirectoryTwoMetadata.permissions
  )

  private val directoryOneMetadataProto = proto.metadata.EntityMetadata(
    entity = proto.metadata.EntityMetadata.Entity.Directory(
      value = actualDirectoryOneMetadata
    )
  )

  private val directoryTwoMetadataProto = proto.metadata.EntityMetadata(
    entity = proto.metadata.EntityMetadata.Entity.Directory(
      value = actualDirectoryTwoMetadata
    )
  )

  private val invalidFileMetadataProto = proto.metadata.EntityMetadata(
    entity = proto.metadata.EntityMetadata.Entity.File(value = actualFileOneMetadata.copy(crate = None))
  )

  private val emtpyMetadataProto = proto.metadata.EntityMetadata(
    entity = proto.metadata.EntityMetadata.Entity.Empty
  )
}
