package stasis.client_android.tracking

import androidx.lifecycle.LiveData
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.state.RecoveryState

interface RecoveryTrackerView {
    val state: LiveData<Map<OperationId, RecoveryState>>
    fun updates(operation: OperationId): LiveData<RecoveryState>
}
