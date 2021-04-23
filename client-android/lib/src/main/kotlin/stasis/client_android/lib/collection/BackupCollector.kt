package stasis.client_android.lib.collection

import kotlinx.coroutines.flow.Flow
import stasis.client_android.lib.model.SourceEntity

interface BackupCollector {
    fun collect(): Flow<SourceEntity>
}
