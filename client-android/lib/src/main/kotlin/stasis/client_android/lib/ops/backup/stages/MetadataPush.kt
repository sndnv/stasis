package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okio.Buffer
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Providers
import java.util.UUID

interface MetadataPush {
    val targetDataset: DatasetDefinition
    val deviceSecret: DeviceSecret
    val providers: Providers

    fun metadataPush(operation: OperationId, flow: Flow<DatasetMetadata>): Flow<Unit> = flow {
        flow.collect { metadata ->
            val entry = pushMetadata(metadata)
            providers.track.metadataPushed(operation, entry)
            emit(Unit)
        }
    }

    private suspend fun pushMetadata(metadata: DatasetMetadata): DatasetEntryId = withContext(Dispatchers.IO) {
        val metadataCrate = UUID.randomUUID()

        val encryptedMetadata = DatasetMetadata.encrypt(
            metadataSecret = deviceSecret.toMetadataSecret(metadataCrate),
            metadata = metadata,
            encoder = providers.encryptor
        )

        val content = Buffer().write(encryptedMetadata)

        val metadataManifest = Manifest(
            crate = metadataCrate,
            origin = providers.clients.core.self,
            source = providers.clients.core.self,
            size = encryptedMetadata.size.toLong(),
            copies = targetDataset.redundantCopies
        )

        val request = CreateDatasetEntry(
            definition = targetDataset.id,
            device = providers.clients.api.self,
            data = metadata.contentChanged.values
                .filterIsInstance<EntityMetadata.File>()
                .flatMap { it.crates.values }
                .toSet(),
            metadata = metadataManifest.crate
        )

        providers.clients.core.push(metadataManifest, content)
        val created = providers.clients.api.createDatasetEntry(request)

        created.entry
    }
}
