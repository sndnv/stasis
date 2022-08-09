package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right

interface MetadataCollection {
    val latestEntry: DatasetEntry?
    val latestMetadata: DatasetMetadata?
    val providers: Providers

    fun metadataCollection(
        operation: OperationId,
        flow: Flow<Either<EntityMetadata, EntityMetadata>>,
        existingState: BackupState?
    ): Flow<DatasetMetadata> =
        flow {
            val existing = existingState?.asMetadataChanges()

            val contentChanged = existing?.first?.toMutableMap() ?: mutableMapOf()
            val metadataChanged = existing?.second?.toMutableMap() ?: mutableMapOf()

            try {
                flow.collect { currentMetadata ->
                    when (currentMetadata) {
                        is Left -> contentChanged += (currentMetadata.value.path to currentMetadata.value)
                        is Right -> metadataChanged += (currentMetadata.value.path to currentMetadata.value)
                    }
                }
            } finally {
                val metadata = DatasetMetadata(
                    contentChanged = contentChanged,
                    metadataChanged = metadataChanged,
                    filesystem = latestMetadata?.let { metadata ->
                        latestEntry?.let { entry ->
                            metadata.filesystem
                                .updated(
                                    changes = contentChanged.keys + metadataChanged.keys,
                                    latestEntry = entry.id
                                )
                        }
                    } ?: FilesystemMetadata(contentChanged.keys + metadataChanged.keys)
                )

                providers.track.metadataCollected(operation)

                emit(metadata)
            }
        }
}
