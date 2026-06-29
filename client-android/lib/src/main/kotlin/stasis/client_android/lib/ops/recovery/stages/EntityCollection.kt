package stasis.client_android.lib.ops.recovery.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Providers
import java.nio.file.FileSystem

interface EntityCollection {
    val targetMetadata: DatasetMetadata
    val keep: (String, FilesystemMetadata.EntityState) -> Boolean
    val destination: TargetEntity.Destination
    val providers: Providers

    fun entityCollection(operation: OperationId, filesystem: FileSystem): Flow<TargetEntity> =
        flow {
            providers.kinds.forEach { kind ->
                emitAll(kind.collector(targetMetadata, keep, destination, providers).collect(filesystem))
            }
        }
            .onEach { entity ->
                providers.track.entityExamined(
                    operation = operation,
                    entity = entity.ref,
                    metadataChanged = entity.hasChanged,
                    contentChanged = entity.hasContentChanged
                )
            }
            .filter { it.hasChanged }
            .onEach { entity ->
                providers.track.entityCollected(operation = operation, entity = entity)
            }
}
