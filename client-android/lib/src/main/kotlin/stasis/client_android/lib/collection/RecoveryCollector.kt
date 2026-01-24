package stasis.client_android.lib.collection

import kotlinx.coroutines.flow.Flow
import stasis.client_android.lib.model.TargetEntity
import java.nio.file.FileSystem

interface RecoveryCollector {
    fun collect(filesystem: FileSystem): Flow<TargetEntity>
}
