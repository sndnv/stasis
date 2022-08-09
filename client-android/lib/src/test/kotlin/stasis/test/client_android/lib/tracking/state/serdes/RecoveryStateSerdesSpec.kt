package stasis.test.client_android.lib.tracking.state.serdes

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.tracking.state.serdes.RecoveryStateSerdes
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.Fixtures

class RecoveryStateSerdesSpec : WordSpec({
    "RecoveryStateSerdes" should {
        val state = mapOf(
            Fixtures.State.RecoveryOneState.operation to Fixtures.State.RecoveryOneState,
            Fixtures.State.RecoveryTwoState.operation to Fixtures.State.RecoveryTwoState
        )

        val protoState = stasis.client_android.lib.model.proto.RecoveryStateCollection(
            collection = mapOf(
                Fixtures.State.RecoveryOneState.operation.toString() to Fixtures.Proto.State.RecoveryOneStateProto,
                Fixtures.State.RecoveryTwoState.operation.toString() to Fixtures.Proto.State.RecoveryTwoStateProto
            )
        ).encode()

        "serialize recovery state to protobuf" {
            RecoveryStateSerdes.serialize(state) shouldBe (protoState)
        }

        "deserialize Recovery state from protobuf" {
            RecoveryStateSerdes.deserialize(protoState) shouldBe (Try.Success(state))
        }
    }
})
