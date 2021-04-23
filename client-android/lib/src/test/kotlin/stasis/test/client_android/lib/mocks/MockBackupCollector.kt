package stasis.test.client_android.lib.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.model.SourceEntity

class MockBackupCollector(val files: List<SourceEntity>) : BackupCollector {
    override fun collect(): Flow<SourceEntity> = files.asFlow()
}
