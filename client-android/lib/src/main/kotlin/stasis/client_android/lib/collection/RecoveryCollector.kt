package stasis.client_android.lib.collection

import kotlinx.coroutines.flow.Flow
import stasis.client_android.lib.model.TargetEntity

interface RecoveryCollector {
    fun collect(): Flow<TargetEntity>
}
