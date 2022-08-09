package stasis.client_android.lib.tracking.state.serdes

import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.persistence.state.StateStore
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Try
import java.util.UUID

object BackupStateSerdes : StateStore.Serdes<Map<OperationId, BackupState>> {
    override fun serialize(state: Map<OperationId, BackupState>): ByteArray =
        stasis.client_android.lib.model.proto.BackupStateCollection(
            collection = state.map { (k, v) -> k.toString() to BackupState.toProto(state = v) }.toMap()
        ).encode()

    override fun deserialize(bytes: ByteArray): Try<Map<OperationId, BackupState>> = Try {
        stasis.client_android.lib.model.proto.BackupStateCollection.ADAPTER
            .decode(bytes)
            .collection
            .map { (k, v) ->
                val operation = UUID.fromString(k)
                operation to BackupState.fromProto(operation = operation, state = v).get()
            }.toMap()
    }
}

