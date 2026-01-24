package stasis.client_android.lib.collection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import java.nio.file.FileSystem
import java.nio.file.Path

class DefaultRecoveryCollector(
    private val targetMetadata: DatasetMetadata,
    private val keep: (String, FilesystemMetadata.EntityState) -> Boolean,
    private val destination: TargetEntity.Destination,
    private val metadataCollector: RecoveryMetadataCollector,
    private val clients: Clients
) : RecoveryCollector {
    override fun collect(filesystem: FileSystem): Flow<TargetEntity> = flow {
        emitAll(
            collectEntityMetadata(targetMetadata, keep, clients).map { entityMetadata ->
                metadataCollector.collect(
                    entity = filesystem.getPath(entityMetadata.path),
                    destination = destination,
                    existingMetadata = entityMetadata
                )
            }.asFlow()
        )
    }

    companion object {
        suspend fun collectEntityMetadata(
            targetMetadata: DatasetMetadata,
            keep: (String, FilesystemMetadata.EntityState) -> Boolean,
            clients: Clients
        ): List<EntityMetadata> =
            targetMetadata.filesystem.entities.toList()
                .filter { (entity, state) -> keep(entity, state) }
                .map { (entity, _) -> targetMetadata.require(entity = entity, clients = clients) }
    }
}
