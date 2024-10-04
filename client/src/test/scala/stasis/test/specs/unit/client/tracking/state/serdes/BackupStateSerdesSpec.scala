package stasis.test.specs.unit.client.tracking.state.serdes

import scala.util.Success

import stasis.client.model.proto
import stasis.client.tracking.state.serdes.BackupStateSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

class BackupStateSerdesSpec extends UnitSpec {
  "BackupStateSerdes" should "serialize backup state to protobuf" in {
    BackupStateSerdes.backupSerdes.serialize(state) should be(protoState)
  }

  they should "deserialize backup state from protobuf" in {
    BackupStateSerdes.backupSerdes.deserialize(protoState) should be(
      Success(state)
    )
  }

  private val state = Map(
    Fixtures.State.BackupOneState.operation -> Fixtures.State.BackupOneState,
    Fixtures.State.BackupTwoState.operation -> Fixtures.State.BackupTwoState
  )

  private val protoState = proto.state
    .BackupStateCollection(
      collection = Map(
        Fixtures.State.BackupOneState.operation.toString -> Fixtures.Proto.State.BackupOneStateProto,
        Fixtures.State.BackupTwoState.operation.toString -> Fixtures.Proto.State.BackupTwoStateProto
      )
    )
    .toByteArray
}
