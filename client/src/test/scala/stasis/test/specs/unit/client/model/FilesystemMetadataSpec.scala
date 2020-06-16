package stasis.test.specs.unit.client.model

import java.nio.file.Paths

import stasis.client.model.{proto, FilesystemMetadata}
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.{Failure, Success}

class FilesystemMetadataSpec extends UnitSpec {
  "A FilesystemMetadata" should "be serializable to protobuf data" in {
    FilesystemMetadata.toProto(
      filesystem = filesystemMetadata
    ) should be(filesystemMetadataProto)
  }

  it should "be deserializable from valid protobuf data" in {
    FilesystemMetadata.fromProto(
      filesystem = Some(filesystemMetadataProto)
    ) should be(Success(filesystemMetadata))
  }

  it should "fail to be deserialized if no metadata is provided" in {
    FilesystemMetadata.fromProto(filesystem = None) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("No filesystem metadata provided")
    }
  }

  it should "allow to be created with new files" in {
    val created = FilesystemMetadata(
      changes = Seq(
        Fixtures.Metadata.FileOneMetadata.path,
        Fixtures.Metadata.FileTwoMetadata.path
      )
    )

    created should be(
      FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.New
        )
      )
    )
  }

  it should "allow to be updated with new files" in {
    val newEntry = DatasetEntry.generateId()

    val newFile = Paths.get("/tmp/file/five")

    val updated = filesystemMetadata.updated(
      changes = Seq(
        Fixtures.Metadata.FileOneMetadata.path, // updated
        newFile // new
      ),
      latestEntry = newEntry
    )

    updated should be(
      FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.Updated,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Existing(newEntry),
          Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.Existing(entry),
          newFile -> FilesystemMetadata.EntityState.New
        )
      )
    )

    val latestEntry = DatasetEntry.generateId()

    updated.updated(changes = Seq.empty, latestEntry = latestEntry) should be(
      FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.Existing(latestEntry),
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Existing(newEntry),
          Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.Existing(entry),
          newFile -> FilesystemMetadata.EntityState.Existing(latestEntry)
        )
      )
    )
  }

  "A Filesystem metadata EntityState" should "be serializable to protobuf data" in {
    FilesystemMetadata.EntityState.toProto(
      state = FilesystemMetadata.EntityState.New
    ) should be(protoEntityStateNew())

    FilesystemMetadata.EntityState.toProto(
      state = FilesystemMetadata.EntityState.Existing(entry)
    ) should be(protoEntityStateExisting(Some(entry)))

    FilesystemMetadata.EntityState.toProto(
      state = FilesystemMetadata.EntityState.Updated
    ) should be(protoEntityStateUpdated())
  }

  it should "be deserializable from valid protobuf data" in {
    FilesystemMetadata.EntityState.fromProto(
      state = protoEntityStateNew()
    ) should be(Success(FilesystemMetadata.EntityState.New))

    FilesystemMetadata.EntityState.fromProto(
      state = protoEntityStateExisting(Some(entry))
    ) should be(Success(FilesystemMetadata.EntityState.Existing(entry)))

    FilesystemMetadata.EntityState.fromProto(
      state = protoEntityStateUpdated()
    ) should be(Success(FilesystemMetadata.EntityState.Updated))
  }

  it should "fail if no entry is provided for an existing file state" in {
    FilesystemMetadata.EntityState.fromProto(state = protoEntityStateExisting(entry = None)) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("No entry ID found for existing file")
    }
  }

  it should "fail if an empty file state is provided" in {
    FilesystemMetadata.EntityState.fromProto(state = protoEntityStateEmpty()) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Unexpected empty file state encountered")
    }
  }

  private val entry = DatasetEntry.generateId()

  private val filesystemMetadata = FilesystemMetadata(
    entities = Map(
      Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
      Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated,
      Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.Existing(entry)
    )
  )

  private val filesystemMetadataProto = proto.metadata.FilesystemMetadata(
    entities = Map(
      Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath.toString -> protoEntityStateNew(),
      Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath.toString -> protoEntityStateUpdated(),
      Fixtures.Metadata.FileThreeMetadata.path.toAbsolutePath.toString -> protoEntityStateExisting(Some(entry))
    )
  )

  private def protoEntityStateNew(): proto.metadata.EntityState =
    proto.metadata.EntityState(
      proto.metadata.EntityState.State.PresentNew(proto.metadata.EntityState.PresentNew())
    )

  private def protoEntityStateExisting(entry: Option[DatasetEntry.Id]): proto.metadata.EntityState =
    proto.metadata.EntityState(
      proto.metadata.EntityState.State
        .PresentExisting(
          proto.metadata.EntityState.PresentExisting(
            entry = entry.map { entry =>
              proto.metadata.Uuid(
                mostSignificantBits = entry.getMostSignificantBits,
                leastSignificantBits = entry.getLeastSignificantBits
              )
            }
          )
        )
    )

  private def protoEntityStateUpdated(): proto.metadata.EntityState =
    proto.metadata.EntityState(
      proto.metadata.EntityState.State.PresentUpdated(proto.metadata.EntityState.PresentUpdated())
    )

  private def protoEntityStateEmpty(): proto.metadata.EntityState =
    proto.metadata.EntityState(
      proto.metadata.EntityState.State.Empty
    )
}
