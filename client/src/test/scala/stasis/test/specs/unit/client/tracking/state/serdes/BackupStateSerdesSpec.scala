package stasis.test.specs.unit.client.tracking.state.serdes

import java.time.Instant
import java.util.UUID

import scala.util.Success

import stasis.client.model.SourceEntity
import stasis.client.model.proto
import stasis.client.tracking.state.BackupState
import stasis.client.tracking.state.serdes.BackupStateSerdes
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers.StringPath

class BackupStateSerdesSpec extends UnitSpec with EncodingHelpers {
  "BackupStateSerdes" should "serialize backup state to protobuf (generated)" in {
    BackupStateSerdes.backupSerdes.serialize(generatedState) should be(generatedStateProto)
  }

  they should "deserialize backup state from protobuf (generated)" in {
    BackupStateSerdes.backupSerdes.deserialize(generatedStateProto) should be(
      Success(generatedState)
    )
  }

  they should "serialize backup state to protobuf (predefined)" in {
    BackupStateSerdes.backupSerdes.serialize(predefinedState) should be(predefinedStateSerialized.decodeFromBase64)
  }

  they should "deserialize backup state from protobuf (predefined)" in {
    BackupStateSerdes.backupSerdes.deserialize(predefinedStateSerialized.decodeFromBase64.toArray) should be(
      Success(predefinedState)
    )
  }

  private val generatedState = Map(
    Fixtures.State.BackupOneState.operation -> Fixtures.State.BackupOneState,
    Fixtures.State.BackupTwoState.operation -> Fixtures.State.BackupTwoState
  )

  private val generatedStateProto = proto.state
    .BackupStateCollection(
      collection = Map(
        Fixtures.State.BackupOneState.operation.toString -> Fixtures.Proto.State.BackupOneStateProto,
        Fixtures.State.BackupTwoState.operation.toString -> Fixtures.Proto.State.BackupTwoStateProto
      )
    )
    .toByteArray

  private val predefinedOperation: Operation.Id = UUID.fromString("1a00ae68-d601-4ade-b849-33c7e50c46cf")

  private val predefinedState: Map[Operation.Id, BackupState] = Map(
    predefinedOperation -> BackupState(
      operation = predefinedOperation,
      definition = UUID.fromString("880a1ccb-d932-4826-80e6-447b33a31d0e"),
      started = Instant.parse("2020-01-02T01:02:03.456Z"),
      entities = BackupState.Entities(
        discovered = Set("/tmp/file/one".asRef),
        unmatched = Seq("a"),
        examined = Set("/tmp/file/two".asRef),
        skipped = Set("/tmp/file/four".asRef),
        collected = Map(
          "/tmp/file/one".asRef -> SourceEntity(
            ref = "/tmp/file/one".asRef,
            existingMetadata = Some(Fixtures.Metadata.FileOneMetadata),
            currentMetadata = Fixtures.Metadata.FileOneMetadata
          )
        ),
        pending = Map(
          "/tmp/file/two".asRef -> BackupState.PendingSourceEntity(expectedParts = 1, processedParts = 2)
        ),
        processed = Map(
          "/tmp/file/one".asRef -> BackupState.ProcessedSourceEntity(
            expectedParts = 1,
            processedParts = 1,
            metadata = Left(Fixtures.Metadata.FileOneMetadata)
          )
        ),
        failed = Map("/tmp/file/four".asRef -> "x")
      ),
      metadataCollected = Some(Instant.parse("2020-01-02T01:02:04.456Z")),
      metadataPushed = Some(Instant.parse("2020-01-02T01:02:05.456Z")),
      failures = Seq("y"),
      completed = Some(Instant.parse("2020-01-02T01:02:06.456Z"))
    )
  )

  private val predefinedStateSerialized: String =
    "CuMECiQxYTAwYWU2OC1kNjAxLTRhZGUtYjg0OS0zM2M3Z" +
      "TUwYzQ2Y2YSugQKJDg4MGExY2NiLWQ5MzItNDgyNi04" +
      "MGU2LTQ0N2IzM2EzMWQwZRDAqZie9i0a8gMKDS90bXA" +
      "vZmlsZS9vbmUSAWEaDS90bXAvZmlsZS90d28ihwIKDS" +
      "90bXAvZmlsZS9vbmUS9QEKDS90bXAvZmlsZS9vbmUSc" +
      "QpvCg0vdG1wL2ZpbGUvb25lEAEogKiQo4Hi+Mf/ATD/" +
      "8dXUr5qHODoEcm9vdEIEcm9vdEoJcnd4cnd4cnd4UgE" +
      "BWigKDy90bXAvZmlsZS9vbmVfMBIVCLiFjYW4/b7PMh" +
      "DK96n/wLee7rEBYgRub25lGnEKbwoNL3RtcC9maWxlL" +
      "29uZRABKICokKOB4vjH/wEw//HV1K+ahzg6BHJvb3RC" +
      "BHJvb3RKCXJ3eHJ3eHJ3eFIBAVooCg8vdG1wL2ZpbGU" +
      "vb25lXzASFQi4hY2FuP2+zzIQyvep/8C3nu6xAWIEbm" +
      "9uZSoVCg0vdG1wL2ZpbGUvdHdvEgQIARACMogBCg0vd" +
      "G1wL2ZpbGUvb25lEncIARABGnEKbwoNL3RtcC9maWxl" +
      "L29uZRABKICokKOB4vjH/wEw//HV1K+ahzg6BHJvb3R" +
      "CBHJvb3RKCXJ3eHJ3eHJ3eFIBAVooCg8vdG1wL2ZpbG" +
      "Uvb25lXzASFQi4hY2FuP2+zzIQyvep/8C3nu6xAWIEb" +
      "m9uZToTCg4vdG1wL2ZpbGUvZm91chIBeEIOL3RtcC9m" +
      "aWxlL2ZvdXIgqLGYnvYtKJC5mJ72LTIBeTj4wJie9i0="
}
