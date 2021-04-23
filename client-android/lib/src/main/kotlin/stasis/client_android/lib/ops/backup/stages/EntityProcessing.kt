package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.core.Manifest
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.ops.backup.stages.internal.PartitionedSource
import stasis.client_android.lib.utils.Either
import stasis.client_android.lib.utils.Either.Left
import stasis.client_android.lib.utils.Either.Right
import stasis.client_android.lib.utils.NonFatal.isNonFatal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import kotlin.math.min

interface EntityProcessing {
    val targetDataset: DatasetDefinition
    val deviceSecret: DeviceSecret
    val providers: Providers

    val maxPartSize: Long

    private val maximumPartSize: Long
        get() = min(maxPartSize, providers.encryptor.maxPlaintextSize)

    fun entityProcessing(
        operation: OperationId,
        flow: Flow<SourceEntity>
    ): Flow<Either<EntityMetadata, EntityMetadata>> =
        flow
            .map { entity ->
                val metadata: Either<EntityMetadata, EntityMetadata> = when {
                    entity.hasContentChanged -> Left(processContentChanged(entity))
                    else -> Right(processMetadataChanged(entity))
                }

                metadata
            }.onEach { metadata ->
                providers.track.entityProcessed(
                    operation = operation,
                    entity = metadata.fold(EntityMetadata::path, EntityMetadata::path),
                    contentChanged = metadata.isLeft
                )
            }


    suspend fun processContentChanged(entity: SourceEntity): EntityMetadata {
        val file = expectFileMetadata(entity)
        val staged = stage(entity.path)
        val crates = push(staged)

        discard(staged)
        return file.copy(crates = crates.toMap())
    }

    suspend fun processMetadataChanged(entity: SourceEntity): EntityMetadata =
        entity.currentMetadata

    suspend fun stage(entity: Path): List<Pair<Path, Path>> = withContext(Dispatchers.IO) {
        fun createPartSecret(partId: Int): DeviceFileSecret =
            deviceSecret.toFileSecret(Paths.get("${entity.toAbsolutePath()}_$partId"))

        PartitionedSource(
            source = entity.source()
                .buffer()
                .apply { providers.compressor.compress(this) },
            providers = providers,
            withPartSecret = ::createPartSecret,
            withMaximumPartSize = maximumPartSize
        ).partitionAndStage()
    }

    suspend fun push(staged: List<Pair<Path, Path>>): List<Pair<Path, CrateId>> =
        try {
            staged
                .map { (partFile, staged) ->
                    val crate = UUID.randomUUID()

                    val content = staged.source()

                    val manifest = Manifest(
                        crate = crate,
                        origin = providers.clients.core.self,
                        source = providers.clients.core.self,
                        size = Files.size(staged),
                        copies = targetDataset.redundantCopies
                    )

                    providers.clients.core.push(manifest, content)

                    Pair(partFile, crate)
                }
        } catch (e: Throwable) {
            if (e.isNonFatal()) {
                discard(staged)
            }
            throw e
        }

    suspend fun discard(staged: List<Pair<Path, Path>>) {
        staged.forEach { (_, staged) ->
            providers.staging.discard(staged)
        }
    }

    companion object {
        fun expectFileMetadata(entity: SourceEntity): EntityMetadata.File =
            when (val metadata = entity.currentMetadata) {
                is EntityMetadata.File -> metadata
                is EntityMetadata.Directory -> throw IllegalArgumentException(
                    "Expected metadata for file but directory metadata for [${metadata.path}] provided"
                )
            }
    }
}
