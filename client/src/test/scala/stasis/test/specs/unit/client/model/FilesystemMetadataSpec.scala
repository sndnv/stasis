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
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.New
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
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.Updated,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Existing(newEntry),
          Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.FileState.Existing(entry),
          newFile -> FilesystemMetadata.FileState.New
        )
      )
    )

    val latestEntry = DatasetEntry.generateId()

    updated.updated(changes = Seq.empty, latestEntry = latestEntry) should be(
      FilesystemMetadata(
        files = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.Existing(latestEntry),
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Existing(newEntry),
          Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.FileState.Existing(entry),
          newFile -> FilesystemMetadata.FileState.Existing(latestEntry)
        )
      )
    )
  }

  "A Filesystem metadata FileState" should "be serializable to protobuf data" in {
    FilesystemMetadata.FileState.toProto(
      state = FilesystemMetadata.FileState.New
    ) should be(protoFileStateNew())

    FilesystemMetadata.FileState.toProto(
      state = FilesystemMetadata.FileState.Existing(entry)
    ) should be(protoFileStateExisting(Some(entry)))

    FilesystemMetadata.FileState.toProto(
      state = FilesystemMetadata.FileState.Updated
    ) should be(protoFileStateUpdated())
  }

  it should "be deserializable from valid protobuf data" in {
    FilesystemMetadata.FileState.fromProto(
      state = protoFileStateNew()
    ) should be(Success(FilesystemMetadata.FileState.New))

    FilesystemMetadata.FileState.fromProto(
      state = protoFileStateExisting(Some(entry))
    ) should be(Success(FilesystemMetadata.FileState.Existing(entry)))

    FilesystemMetadata.FileState.fromProto(
      state = protoFileStateUpdated()
    ) should be(Success(FilesystemMetadata.FileState.Updated))
  }

  it should "fail if no entry is provided for an existing file state" in {
    FilesystemMetadata.FileState.fromProto(state = protoFileStateExisting(entry = None)) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("No entry ID found for existing file")
    }
  }

  it should "fail if an empty file state is provided" in {
    FilesystemMetadata.FileState.fromProto(state = protoFileStateEmpty()) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("Unexpected empty file state encountered")
    }
  }

  private val entry = DatasetEntry.generateId()

  private val filesystemMetadata = FilesystemMetadata(
    files = Map(
      Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.FileState.New,
      Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.FileState.Updated,
      Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.FileState.Existing(entry)
    )
  )

  private val filesystemMetadataProto = proto.metadata.FilesystemMetadata(
    files = Map(
      Fixtures.Metadata.FileOneMetadata.path.toAbsolutePath.toString -> protoFileStateNew(),
      Fixtures.Metadata.FileTwoMetadata.path.toAbsolutePath.toString -> protoFileStateUpdated(),
      Fixtures.Metadata.FileThreeMetadata.path.toAbsolutePath.toString -> protoFileStateExisting(Some(entry))
    )
  )

  private def protoFileStateNew(): proto.metadata.FileState = proto.metadata.FileState(
    proto.metadata.FileState.State.PresentNew(proto.metadata.FileState.PresentNew())
  )

  private def protoFileStateExisting(entry: Option[DatasetEntry.Id]): proto.metadata.FileState =
    proto.metadata.FileState(
      proto.metadata.FileState.State
        .PresentExisting(
          proto.metadata.FileState.PresentExisting(
            entry = entry.map { entry =>
              proto.metadata.Uuid(
                mostSignificantBits = entry.getMostSignificantBits,
                leastSignificantBits = entry.getLeastSignificantBits
              )
            }
          )
        )
    )

  private def protoFileStateUpdated(): proto.metadata.FileState = proto.metadata.FileState(
    proto.metadata.FileState.State.PresentUpdated(proto.metadata.FileState.PresentUpdated())
  )

  private def protoFileStateEmpty(): proto.metadata.FileState = proto.metadata.FileState(
    proto.metadata.FileState.State.Empty
  )
}
