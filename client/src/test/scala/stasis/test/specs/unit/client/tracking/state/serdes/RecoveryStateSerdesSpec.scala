package stasis.test.specs.unit.client.tracking.state.serdes

import java.time.Instant
import java.util.UUID

import scala.util.Success

import stasis.client.model.TargetEntity
import stasis.client.model.proto
import stasis.client.tracking.state.RecoveryState
import stasis.client.tracking.state.serdes.RecoveryStateSerdes
import stasis.shared.ops.Operation
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.EncodingHelpers
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.ResourceHelpers.StringPath

class RecoveryStateSerdesSpec extends UnitSpec with EncodingHelpers {
  "RecoveryStateSerdes" should "serialize recovery state to protobuf (generated)" in {
    RecoveryStateSerdes.recoverySerdes.serialize(generatedState) should be(generatedStateProto)
  }

  they should "deserialize Recovery state from protobuf (generated)" in {
    RecoveryStateSerdes.recoverySerdes.deserialize(generatedStateProto) should be(
      Success(generatedState)
    )
  }

  they should "serialize recovery state to protobuf (predefined)" in {
    RecoveryStateSerdes.recoverySerdes.serialize(predefinedState) should be(predefinedStateSerialized.decodeFromBase64)
  }

  they should "deserialize Recovery state from protobuf (predefined)" in {
    RecoveryStateSerdes.recoverySerdes.deserialize(predefinedStateSerialized.decodeFromBase64.toArray) should be(
      Success(predefinedState)
    )
  }

  private val generatedState = Map(
    Fixtures.State.RecoveryOneState.operation -> Fixtures.State.RecoveryOneState,
    Fixtures.State.RecoveryTwoState.operation -> Fixtures.State.RecoveryTwoState
  )

  private val generatedStateProto = proto.state
    .RecoveryStateCollection(
      collection = Map(
        Fixtures.State.RecoveryOneState.operation.toString -> Fixtures.Proto.State.RecoveryOneStateProto,
        Fixtures.State.RecoveryTwoState.operation.toString -> Fixtures.Proto.State.RecoveryTwoStateProto
      )
    )
    .toByteArray

  private val predefinedOperation: Operation.Id = UUID.fromString("79879d7a-4113-4b2a-9301-62a5c81343b3")

  private val predefinedState: Map[Operation.Id, RecoveryState] = Map(
    predefinedOperation -> RecoveryState(
      operation = predefinedOperation,
      started = Instant.parse("2020-01-02T02:03:04.567Z"),
      entities = RecoveryState.Entities(
        examined = Set("/tmp/file/one".asRef),
        collected = Map(
          "/tmp/file/one".asRef -> TargetEntity(
            ref = "/tmp/file/one".asRef,
            destination = TargetEntity.Destination.Default,
            existingMetadata = Fixtures.Metadata.FileOneMetadata,
            currentMetadata = Some(Fixtures.Metadata.FileOneMetadata)
          )
        ),
        pending = Map(
          "/tmp/file/two".asRef -> RecoveryState.PendingTargetEntity(expectedParts = 3, processedParts = 1)
        ),
        processed = Map(
          "/tmp/file/one".asRef -> RecoveryState.ProcessedTargetEntity(expectedParts = 1, processedParts = 1)
        ),
        metadataApplied = Set("/tmp/file/one".asRef),
        failed = Map("/tmp/file/four".asRef -> "x")
      ),
      failures = Seq("y"),
      completed = Some(Instant.parse("2020-01-02T02:03:05.567Z"))
    )
  )

  private val predefinedStateSerialized: String =
    "CqwDCiQ3OTg3OWQ3YS00MTEzLTRiMmEtOTMwMS02MmE1Y" +
      "zgxMzQzYjMSgwMI9+P3n/YtEu8CCg0vdG1wL2ZpbGUv" +
      "b25lEosCCg0vdG1wL2ZpbGUvb25lEvkBCg0vdG1wL2Z" +
      "pbGUvb25lEgIKABpxCm8KDS90bXAvZmlsZS9vbmUQAS" +
      "iAqJCjgeL4x/8BMP/x1dSvmoc4OgRyb290QgRyb290S" +
      "glyd3hyd3hyd3hSAQFaKAoPL3RtcC9maWxlL29uZV8w" +
      "EhUIuIWNhbj9vs8yEMr3qf/At57usQFiBG5vbmUicQp" +
      "vCg0vdG1wL2ZpbGUvb25lEAEogKiQo4Hi+Mf/ATD/8d" +
      "XUr5qHODoEcm9vdEIEcm9vdEoJcnd4cnd4cnd4UgEBW" +
      "igKDy90bXAvZmlsZS9vbmVfMBIVCLiFjYW4/b7PMhDK" +
      "96n/wLee7rEBYgRub25lGhUKDS90bXAvZmlsZS90d28" +
      "SBAgDEAEiFQoNL3RtcC9maWxlL29uZRIECAEQASoNL3" +
      "RtcC9maWxlL29uZTITCg4vdG1wL2ZpbGUvZm91chIBe" +
      "BoBeSDf6/ef9i0="
}
