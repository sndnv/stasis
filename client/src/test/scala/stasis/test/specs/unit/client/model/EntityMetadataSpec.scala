package stasis.test.specs.unit.client.model

import stasis.client.model.EntityMetadata
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.{Failure, Success}

class EntityMetadataSpec extends UnitSpec {
  "A EntityMetadata" should "be serializable to protobuf data" in {
    EntityMetadata.toProto(Fixtures.Metadata.FileOneMetadata) should be(Fixtures.Proto.Metadata.FileOneMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.DirectoryOneMetadata) should be(Fixtures.Proto.Metadata.DirectoryOneMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.FileTwoMetadata) should be(Fixtures.Proto.Metadata.FileTwoMetadataProto)
    EntityMetadata.toProto(Fixtures.Metadata.DirectoryTwoMetadata) should be(Fixtures.Proto.Metadata.DirectoryTwoMetadataProto)
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
}
