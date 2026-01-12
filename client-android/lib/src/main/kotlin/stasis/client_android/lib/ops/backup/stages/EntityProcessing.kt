package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
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
import stasis.client_android.lib.utils.NonFatal.nonFatal
import stasis.client_android.lib.utils.Try
import java.math.BigInteger
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
        flow.map { entity ->
            entity to Try {
                val metadata: Either<EntityMetadata, EntityMetadata> = when {
                    entity.hasContentChanged -> Left(processContentChanged(operation, entity))
                    else -> Right(processMetadataChanged(operation, entity))
                }

                metadata
            }
        }.mapNotNull { (entity, result) ->
            when (result) {
                is Try.Success -> {
                    providers.track.entityProcessed(
                        operation = operation,
                        entity = entity.path,
                        metadata = result.value
                    )

                    result.value
                }

                is Try.Failure -> {
                    providers.track.failureEncountered(
                        operation = operation,
                        entity = entity.path,
                        failure = result.exception.nonFatal()
                    )

                    if (result.exception is EndpointFailure) {
                        throw result.exception
                    } else {
                        null
                    }
                }
            }
        }

    suspend fun processContentChanged(operation: OperationId, entity: SourceEntity): EntityMetadata {
        val file = expectFileMetadata(entity)
        val staged = stage(operation, entity, file.checksum)
        val crates = push(staged)

        discard(staged)

        return file.copy(crates = crates.toMap())
    }

    suspend fun processMetadataChanged(operation: OperationId, entity: SourceEntity): EntityMetadata {
        providers.track.entityProcessingStarted(operation = operation, entity = entity.path, expectedParts = 0)
        return entity.currentMetadata
    }

    suspend fun stage(
        operation: OperationId,
        entity: SourceEntity,
        checksum: BigInteger
    ): List<Pair<Path, Path>> = withContext(Dispatchers.IO) {
        providers.track.entityProcessingStarted(
            operation = operation,
            entity = entity.path,
            expectedParts = expectedParts(entity, maximumPartSize)
        )

        fun createPartSecret(partId: Int): DeviceFileSecret =
            deviceSecret.toFileSecret(
                forFile = Paths.get("${entity.path.toAbsolutePath()}__part=$partId"),
                checksum = checksum
            )

        fun recordPartProcessed(): Unit =
            providers.track.entityPartProcessed(operation = operation, entity = entity.path)

        PartitionedSource(
            source = entity.path.source()
                .buffer()
                .let { providers.compression.encoderFor(entity).compress(it).buffer() },
            providers = providers,
            withPartSecret = ::createPartSecret,
            onPartStaged = ::recordPartProcessed,
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

        fun expectedParts(entity: SourceEntity, withMaximumPartSize: Long): Int {
            require(withMaximumPartSize > 0) { "Invalid [maximumPartSize] provided: [$withMaximumPartSize]" }

            return when (val metadata = entity.currentMetadata) {
                is EntityMetadata.File -> if (entity.hasContentChanged) {
                    val fullParts = (metadata.size / withMaximumPartSize).toInt()
                    if (metadata.size % withMaximumPartSize == 0L) fullParts else fullParts + 1
                } else 0

                else -> 0
            }
        }
    }
}
