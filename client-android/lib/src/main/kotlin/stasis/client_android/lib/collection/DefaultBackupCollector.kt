package stasis.client_android.lib.collection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import java.nio.file.Path

class DefaultBackupCollector(
    private val entities: List<Path>,
    private val latestMetadata: DatasetMetadata?,
    private val metadataCollector: BackupMetadataCollector,
    private val clients: Clients
) : BackupCollector {
    override fun collect(): Flow<SourceEntity> =
        collectEntityMetadata(
            entities = entities,
            latestMetadata = latestMetadata,
            clients = clients
        ).map { (entity, entityMetadata) ->
            metadataCollector.collect(entity = entity, existingMetadata = entityMetadata)
        }


    companion object {
        fun collectEntityMetadata(
            entities: List<Path>,
            latestMetadata: DatasetMetadata?,
            clients: Clients
        ): Flow<Pair<Path, EntityMetadata?>> =
            when (latestMetadata) {
                null -> entities.asFlow().map { entity -> entity to null }
                else -> entities.asFlow().map { entity -> entity to latestMetadata.collect(entity, clients) }
            }
    }
}
