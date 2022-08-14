package stasis.client_android.tracking

import androidx.lifecycle.LiveData
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.state.BackupState

interface BackupTrackerView : BackupTrackerManage {
    val state: LiveData<Map<OperationId, BackupState>>
    fun updates(operation: OperationId): LiveData<BackupState>
}
