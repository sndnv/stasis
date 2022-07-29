package stasis.test.specs.unit.client.tracking.state.serdes

import stasis.client.model.proto
import stasis.client.tracking.state.serdes.RecoveryStateSerdes
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.Fixtures

import scala.util.Success

class RecoveryStateSerdesSpec extends UnitSpec {
  "RecoveryStateSerdes" should "serialize recovery state to protobuf" in {
    RecoveryStateSerdes.recoverySerdes.serialize(state) should be(protoState)
  }

  they should "deserialize Recovery state from protobuf" in {
    RecoveryStateSerdes.recoverySerdes.deserialize(protoState) should be(
      Success(state)
    )
  }

  private val state = Map(
    Fixtures.State.RecoveryOneState.operation -> Fixtures.State.RecoveryOneState,
    Fixtures.State.RecoveryTwoState.operation -> Fixtures.State.RecoveryTwoState
  )

  private val protoState = proto.state
    .RecoveryStateCollection(
      collection = Map(
        Fixtures.State.RecoveryOneState.operation.toString -> Fixtures.Proto.State.RecoveryOneStateProto,
        Fixtures.State.RecoveryTwoState.operation.toString -> Fixtures.Proto.State.RecoveryTwoStateProto
      )
    )
    .toByteArray
}
