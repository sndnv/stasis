package stasis.test.specs.unit.client.model

import stasis.client.model.{proto, FileMetadata}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.{Failure, Success}

class FileMetadataSpec extends UnitSpec {
  private val fileMetadataProto = proto.metadata.FileMetadata(
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
      proto.metadata.CrateId(
        mostSignificantBits = Fixtures.Metadata.FileOneMetadata.crate.getMostSignificantBits,
        leastSignificantBits = Fixtures.Metadata.FileOneMetadata.crate.getLeastSignificantBits
      )
    )
  )

  "A FileMetadata" should "be serializable to protobuf data" in {
    FileMetadata.toProto(fileMetadata = Fixtures.Metadata.FileOneMetadata) should be(fileMetadataProto)
  }

  it should "be deserializable from valid protobuf data" in {
    FileMetadata.fromProto(fileMetadata = fileMetadataProto) should be(Success(Fixtures.Metadata.FileOneMetadata))
  }

  it should "fail to be deserialized from invalid protobuf data" in {
    FileMetadata.fromProto(fileMetadata = fileMetadataProto.copy(crate = None)) match {
      case Success(metadata) => fail(s"Unexpected successful result received: [$metadata]")
      case Failure(e)        => e shouldBe a[IllegalArgumentException]
    }
  }
}
