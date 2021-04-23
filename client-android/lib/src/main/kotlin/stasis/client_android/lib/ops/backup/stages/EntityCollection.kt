package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Providers

interface EntityCollection {
    val targetDataset: DatasetDefinition
    val providers: Providers
    val collector: BackupCollector

    fun entityCollection(operation: OperationId): Flow<SourceEntity> =
        collector
            .collect()
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
                providers.track.entityCollected(operation = operation, entity = entity.path)
            }
}
