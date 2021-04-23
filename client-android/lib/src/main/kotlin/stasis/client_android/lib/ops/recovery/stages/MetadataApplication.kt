package stasis.client_android.lib.ops.recovery.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Providers

interface MetadataApplication {
    val providers: Providers

    fun metadataApplication(operation: OperationId, flow: Flow<TargetEntity>): Flow<TargetEntity> =
        flow.map { targetEntity ->
            Metadata.applyEntityMetadataTo(
                metadata = targetEntity.existingMetadata,
                entity = targetEntity.destinationPath
            )

            targetEntity
        }.onEach { targetEntity ->
            providers.track.metadataApplied(
                operation = operation,
                entity = targetEntity.destinationPath
            )
        }
}
