package stasis.client_android.lib.tracking.state

import stasis.client_android.lib.ops.Operation
import java.time.Instant

interface OperationState {
    val type: Operation.Type
    val completed: Instant?
    fun asProgress(): Operation.Progress
}
