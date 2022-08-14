package stasis.client_android.tracking

import stasis.client_android.lib.ops.OperationId

interface RecoveryTrackerManage {
    fun remove(operation: OperationId)
    fun clear()
}
