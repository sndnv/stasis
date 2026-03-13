package stasis.test.specs.unit.client.model

import scala.util.Failure
import scala.util.Success

import stasis.client.model.FilesystemMetadata
import stasis.client.model.proto
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class FilesystemMetadataSpec extends UnitSpec {
  "A FilesystemMetadata" should "be serializable to protobuf data" in {
    FilesystemMetadata.toProto(
      filesystem = createFilesystemMetadata()
    ) should be(filesystemMetadataProtoWithoutSeparator) // default separator used
  }

  it should "be deserializable from valid protobuf data (with separator)" in {
    FilesystemMetadata.fromProto(
      filesystem = Some(filesystemMetadataProtoWithSeparator)
    ) should be(Success(createFilesystemMetadata(separator = "?")))
  }

  it should "be deserializable from valid protobuf data (without separator)" in {
    FilesystemMetadata.fromProto(
      filesystem = Some(filesystemMetadataProtoWithoutSeparator)
    ) should be(Success(createFilesystemMetadata()))
  }

  it should "fail to be deserialized if no metadata is provided" in {
    FilesystemMetadata.fromProto(filesystem = None) match {
      case Success(result) => fail(s"Unexpected result received: [$result]")
      case Failure(e)      => e.getMessage should be("No filesystem metadata provided")
    }
  }

  it should "support creation with new files" in {
    val created = FilesystemMetadata(
      changes = Seq(
        Fixtures.Metadata.FileOneMetadata.path,
        Fixtures.Metadata.FileTwoMetadata.path
      ),
      filesystemSeparator = "/"
    )

    created should be(
      FilesystemMetadata(
        entities = Map(
          Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
          Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.New
        ),
        filesystemSeparator = "/"
      )
    )
  }

  it should "support updating with new files" in {
    val newEntry = DatasetEntry.generateId()

    val newFile = "/tmp/file/five"

    val updated = createFilesystemMetadata().updated(
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
        ),
        filesystemSeparator = "/"
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
        ),
        filesystemSeparator = "/"
      )
    )
  }

  it should "support metadata collection" in {
    val collected = createFilesystemMetadata().collect {
      case (path, state) if state == FilesystemMetadata.EntityState.Updated => path
    }

    collected should be(Seq("/tmp/file/two"))
  }

  it should "support metadata retrieval" in {
    val filesystemMetadata = createFilesystemMetadata()
    filesystemMetadata.get("/tmp/file/one") should be(Some(FilesystemMetadata.EntityState.New))
    filesystemMetadata.get("/tmp/file/two") should be(Some(FilesystemMetadata.EntityState.Updated))
    filesystemMetadata.get("/tmp/file/other") should be(None)
  }

  it should "support metadata search" in {
    val result = createFilesystemMetadata().search(".*(two|four)$".r.pattern)

    result.size should be(2)
    result.get("/tmp/file/two") should be(Some(FilesystemMetadata.EntityState.Updated))
    result.get("/tmp/file/four") should be(Some(FilesystemMetadata.EntityState.Existing(entry)))
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

  private def createFilesystemMetadata(separator: String = "/") = FilesystemMetadata(
    entities = Map(
      Fixtures.Metadata.FileOneMetadata.path -> FilesystemMetadata.EntityState.New,
      Fixtures.Metadata.FileTwoMetadata.path -> FilesystemMetadata.EntityState.Updated,
      Fixtures.Metadata.FileThreeMetadata.path -> FilesystemMetadata.EntityState.Existing(entry)
    ),
    filesystemSeparator = separator
  )

  private val filesystemMetadataProtoWithSeparator = proto.metadata.FilesystemMetadata(
    entities = Map(
      Fixtures.Metadata.FileOneMetadata.path -> protoEntityStateNew(),
      Fixtures.Metadata.FileTwoMetadata.path -> protoEntityStateUpdated(),
      Fixtures.Metadata.FileThreeMetadata.path -> protoEntityStateExisting(Some(entry))
    ),
    separator = "?"
  )

  private val filesystemMetadataProtoWithoutSeparator = proto.metadata.FilesystemMetadata(
    entities = Map(
      Fixtures.Metadata.FileOneMetadata.path -> protoEntityStateNew(),
      Fixtures.Metadata.FileTwoMetadata.path -> protoEntityStateUpdated(),
      Fixtures.Metadata.FileThreeMetadata.path -> protoEntityStateExisting(Some(entry))
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
