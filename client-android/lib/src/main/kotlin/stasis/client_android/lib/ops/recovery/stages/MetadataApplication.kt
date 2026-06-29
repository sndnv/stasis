package stasis.client_android.lib.ops.recovery.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind

interface MetadataApplication {
    val providers: Providers

    fun metadataApplication(operation: OperationId, flow: Flow<TargetEntity>): Flow<TargetEntity> =
        flow.map { targetEntity ->
            RecoveryEntityKind.applyMetadata(providers.kinds, targetEntity, providers)

            targetEntity
        }.onEach { targetEntity ->
            providers.track.metadataApplied(
                operation = operation,
                entity = targetEntity.destinationRef
            )
        }
}
