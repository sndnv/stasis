package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind

class MockRecoveryEntityKind(
    private val recoveryCollector: RecoveryCollector
) : RecoveryEntityKind.Filesystem by RecoveryEntityKind.Filesystem {
    override fun collector(
        targetMetadata: DatasetMetadata,
        keep: (String, FilesystemMetadata.EntityState) -> Boolean,
        destination: TargetEntity.Destination,
        providers: Providers
    ): RecoveryCollector = recoveryCollector
}
