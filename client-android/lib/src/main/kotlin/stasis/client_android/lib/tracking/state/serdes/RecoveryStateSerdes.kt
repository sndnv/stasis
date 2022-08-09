package stasis.client_android.lib.tracking.state.serdes

import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.persistence.state.StateStore
import stasis.client_android.lib.tracking.state.RecoveryState
import stasis.client_android.lib.utils.Try
import java.util.UUID

object RecoveryStateSerdes : StateStore.Serdes<Map<OperationId, RecoveryState>> {
    override fun serialize(state: Map<OperationId, RecoveryState>): ByteArray =
        stasis.client_android.lib.model.proto.RecoveryStateCollection(
            collection = state.map { (k, v) -> k.toString() to RecoveryState.toProto(state = v) }.toMap()
        ).encode()

    override fun deserialize(bytes: ByteArray): Try<Map<OperationId, RecoveryState>> = Try {
        stasis.client_android.lib.model.proto.RecoveryStateCollection.ADAPTER
            .decode(bytes)
            .collection
            .map { (k, v) ->
                val operation = UUID.fromString(k)
                operation to RecoveryState.fromProto(operation = operation, state = v).get()
            }.toMap()
    }
}
