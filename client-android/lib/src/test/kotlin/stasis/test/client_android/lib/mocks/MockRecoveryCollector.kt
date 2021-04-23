package stasis.test.client_android.lib.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.model.TargetEntity

class MockRecoveryCollector(val files: List<TargetEntity>) : RecoveryCollector {
    override fun collect(): Flow<TargetEntity> = files.asFlow()
}
