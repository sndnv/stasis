package stasis.test.specs.unit.client.model

import scala.util.Failure
import scala.util.Success

import org.apache.pekko.util.ByteString

import stasis.client.model.EntityMetadata
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class EntityMetadataSpec extends UnitSpec {
  "A EntityMetadata" should "be serializable to protobuf data" in {
    EntityMetadata.toProto(Fixtures.Metadata.FileOneMetadata) should be(Fixtures.Proto.Metadata.FileOneMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.DirectoryOneMetadata) should be(Fixtures.Proto.Metadata.DirectoryOneMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.FileTwoMetadata) should be(Fixtures.Proto.Metadata.FileTwoMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.DirectoryTwoMetadata) should be(Fixtures.Proto.Metadata.DirectoryTwoMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.LibraryOneMetadata) should be(Fixtures.Proto.Metadata.LibraryOneMetadataProto)
  }

  it should "be deserializable from valid protobuf data" in {
    EntityMetadata.fromProto(Fixtures.Proto.Metadata.FileOneMetadataProto) should be(
      Success(Fixtures.Metadata.FileOneMetadata)
    )

    EntityMetadata.fromProto(Fixtures.Proto.Metadata.DirectoryOneMetadataProto) should be(
      Success(Fixtures.Metadata.DirectoryOneMetadata)
    )

    EntityMetadata.fromProto(Fixtures.Proto.Metadata.FileTwoMetadataProto) should be(
      Success(Fixtures.Metadata.FileTwoMetadata)
    )

    EntityMetadata.fromProto(Fixtures.Proto.Metadata.DirectoryTwoMetadataProto) should be(
      Success(Fixtures.Metadata.DirectoryTwoMetadata)
    )

    EntityMetadata.fromProto(Fixtures.Proto.Metadata.LibraryOneMetadataProto) should be(
      Success(Fixtures.Metadata.LibraryOneMetadata)
    )
  }

  it should "fail to be deserialized when empty entity is provided" in {
    EntityMetadata.fromProto(entityMetadata = Fixtures.Proto.Metadata.EmptyMetadataProto) match {
      case Success(metadata) => fail(s"Unexpected successful result received: [$metadata]")
      case Failure(e)        => e shouldBe an[IllegalArgumentException]
    }
  }

  it should "support comparing metadata for changes, ignoring file compression" in {
    Fixtures.Metadata.FileOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.FileOneMetadata) should be(false)

    Fixtures.Metadata.FileOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.FileTwoMetadata) should be(true)

    Fixtures.Metadata.FileOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.FileThreeMetadata) should be(true)

    Fixtures.Metadata.FileOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.FileOneMetadata.copy(compression = "other")) should be(false)

    Fixtures.Metadata.FileOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.DirectoryOneMetadata) should be(true)

    Fixtures.Metadata.DirectoryOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.DirectoryOneMetadata) should be(false)

    Fixtures.Metadata.DirectoryOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.DirectoryTwoMetadata) should be(true)
  }

  it should "support comparing library metadata for changes, ignoring compression but not attributes" in {
    Fixtures.Metadata.LibraryOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.LibraryOneMetadata) should be(false)

    Fixtures.Metadata.LibraryOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.LibraryOneMetadata.copy(compression = "other")) should be(false)

    Fixtures.Metadata.LibraryOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.LibraryOneMetadata.copy(attributes = ByteString("favorite=false"))) should be(
      true
    )

    Fixtures.Metadata.LibraryOneMetadata
      .hasChanged(comparedTo = Fixtures.Metadata.FileOneMetadata) should be(true)
  }

  it should "provide content-bearing metadata with updated crates and compression" in {
    val updatedCrates = Map("photos:/album/img.heic_0" -> java.util.UUID.randomUUID())

    Fixtures.Metadata.LibraryOneMetadata.withCrates(updatedCrates).crates should be(updatedCrates)
    Fixtures.Metadata.LibraryOneMetadata.withCompression("gzip").compression should be("gzip")

    Fixtures.Metadata.FileOneMetadata.withCrates(updatedCrates).crates should be(updatedCrates)
    Fixtures.Metadata.FileOneMetadata.withCompression("gzip").compression should be("gzip")
  }

  it should "support coercion to filesystem metadata" in {
    Fixtures.Metadata.FileOneMetadata.asFilesystem should be(Fixtures.Metadata.FileOneMetadata)
    Fixtures.Metadata.DirectoryOneMetadata.asFilesystem should be(Fixtures.Metadata.DirectoryOneMetadata)

    val e = intercept[IllegalArgumentException](Fixtures.Metadata.LibraryOneMetadata.asFilesystem)
    e.getMessage should be("Requested filesystem metadata but library metadata for [photos:/album/img.heic] found")
  }
}
