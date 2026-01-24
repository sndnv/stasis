package stasis.test.client_android.lib.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.model.TargetEntity
import java.nio.file.FileSystem

class MockRecoveryCollector(val files: List<TargetEntity>) : RecoveryCollector {
    override fun collect(filesystem: FileSystem): Flow<TargetEntity> = files.asFlow()
}
