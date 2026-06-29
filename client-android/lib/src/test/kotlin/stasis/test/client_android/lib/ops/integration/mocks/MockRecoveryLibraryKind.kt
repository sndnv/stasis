package stasis.test.client_android.lib.ops.integration.mocks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.Source
import okio.buffer
import stasis.client_android.lib.collection.RecoveryCollector
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind
import java.nio.file.FileSystem

class MockRecoveryLibraryKind(private val library: MockLibrary) : RecoveryEntityKind.Library {
    override val scheme: String = library.scheme

    override fun collector(
        targetMetadata: DatasetMetadata,
        keep: (String, FilesystemMetadata.EntityState) -> Boolean,
        destination: TargetEntity.Destination,
        providers: Providers
    ): RecoveryCollector =
        object : RecoveryCollector {
            override fun collect(filesystem: FileSystem): Flow<TargetEntity> = flow {
                val entities = targetMetadata.filesystem.collect { entity, state ->
                    if (SourceUri.scheme(entity) == scheme && keep(entity, state)) entity else null
                }

                entities.forEach { entity ->
                    emit(
                        TargetEntity(
                            ref = EntityRef.default(entity),
                            destination = destination,
                            existingMetadata = targetMetadata.require(entity = entity, clients = providers.clients),
                            currentMetadata = null
                        )
                    )
                }
            }
        }

    override fun prepare(entity: TargetEntity, ref: EntityRef.Library, providers: Providers) = Unit

    override suspend fun write(entity: TargetEntity, ref: EntityRef.Library, content: Source, providers: Providers) {
        library.restored[ref.key] = content.buffer().readByteString()
    }

    override suspend fun applyMetadata(entity: TargetEntity, ref: EntityRef.Library, providers: Providers) {
        when (val metadata = entity.existingMetadata) {
            is EntityMetadata.Library -> library.restoredAttributes[ref.key] = metadata.attributes
            else -> Unit
        }
    }
}
