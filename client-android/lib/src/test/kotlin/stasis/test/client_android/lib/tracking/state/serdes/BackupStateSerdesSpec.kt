package stasis.test.client_android.lib.tracking.state.serdes

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.tracking.state.serdes.BackupStateSerdes
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.Fixtures

class BackupStateSerdesSpec : WordSpec({
    "BackupStateSerdes" should {
        val state = mapOf(
            Fixtures.State.BackupOneState.operation to Fixtures.State.BackupOneState,
            Fixtures.State.BackupTwoState.operation to Fixtures.State.BackupTwoState
        )

        val protoState = stasis.client_android.lib.model.proto.BackupStateCollection(
            collection = mapOf(
                Fixtures.State.BackupOneState.operation.toString() to Fixtures.Proto.State.BackupOneStateProto,
                Fixtures.State.BackupTwoState.operation.toString() to Fixtures.Proto.State.BackupTwoStateProto
            )
        ).encode()

        "serialize backup state to protobuf" {
            BackupStateSerdes.serialize(state) shouldBe (protoState)
        }

        "deserialize backup state from protobuf" {
            BackupStateSerdes.deserialize(protoState) shouldBe (Try.Success(state))
        }
    }
})
