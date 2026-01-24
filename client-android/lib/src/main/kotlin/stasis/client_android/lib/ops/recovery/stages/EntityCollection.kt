package stasis.client_android.lib.ops.recovery.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Providers
import java.nio.file.FileSystem

interface EntityCollection {
    val collector: RecoveryCollector
    val providers: Providers

    fun entityCollection(operation: OperationId, filesystem: FileSystem): Flow<TargetEntity> =
        collector
            .collect(filesystem)
            .onEach { entity ->
                providers.track.entityExamined(
                    operation = operation,
                    entity = entity.path,
                    metadataChanged = entity.hasChanged,
                    contentChanged = entity.hasContentChanged
                )
            }
            .filter { it.hasChanged }
            .onEach { entity ->
                providers.track.entityCollected(operation = operation, entity = entity)
            }
}
