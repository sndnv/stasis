package stasis.client_android.lib.ops.recovery.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import okio.Source
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Providers
import stasis.client_android.lib.ops.recovery.RecoveryEntityKind
import stasis.client_android.lib.ops.recovery.stages.internal.DecompressedSource.decompress
import stasis.client_android.lib.ops.recovery.stages.internal.DecryptedCrates.decrypt
import stasis.client_android.lib.ops.recovery.stages.internal.MergedCrates.merged
import stasis.client_android.lib.utils.NonFatal.nonFatal
import stasis.client_android.lib.utils.StringPaths.extractName
import stasis.client_android.lib.utils.Try
import java.nio.file.FileSystem
import java.nio.file.FileSystems

interface EntityProcessing {
    val deviceSecret: DeviceSecret
    val providers: Providers

    fun entityProcessing(operation: OperationId, flow: Flow<TargetEntity>): Flow<TargetEntity> =
        flow.filter { entity ->
            when (val destination = entity.destination) {
                is TargetEntity.Destination.Default -> true
                is TargetEntity.Destination.Directory -> when {
                    destination.keepDefaultStructure -> true
                    entity.existingMetadata is EntityMetadata.File -> true
                    entity.existingMetadata is EntityMetadata.Library -> true
                    else -> false
                }
            }
        }
            .map { entity ->
                RecoveryEntityKind.prepare(providers.kinds, entity, providers)
                entity
            }
            .map { entity ->
                entity to Try {
                    when {
                        entity.hasContentChanged -> processContentChanged(operation, entity)
                        else -> processMetadataChanged(operation, entity)
                    }
                }
            }
            .mapNotNull { (entity, result) ->
                when (result) {
                    is Try.Success -> {
                        providers.track.entityProcessed(
                            operation = operation,
                            entity = entity.destinationRef
                        )
                        entity
                    }

                    is Try.Failure -> {
                        providers.track.failureEncountered(
                            operation = operation,
                            entity = entity.ref,
                            failure = result.exception.nonFatal()
                        )
                        providers.analytics.recordFailure(result.exception)

                        if (result.exception is EndpointFailure) {
                            throw result.exception
                        } else {
                            null
                        }
                    }
                }
            }

    private suspend fun processContentChanged(operation: OperationId, entity: TargetEntity): TargetEntity {
        val contentMetadata = expectContentMetadata(entity)
        val crates = contentMetadata.crates

        providers.track.entityProcessingStarted(
            operation = operation,
            entity = entity.ref,
            expectedParts = crates.size
        )

        fun recordPartProcessed(): Unit =
            providers.track.entityPartProcessed(operation, entity = entity.ref)

        val content = pull(crates, entity.originalRef.key)
            .decrypt(withPartSecret = { deviceSecret.toFileSecret(it, contentMetadata.checksum) }, providers = providers)
            .merged(onPartProcessed = ::recordPartProcessed)
            .decompress(decompressor = providers.compression.decoderFor(entity))

        RecoveryEntityKind.write(providers.kinds, entity, content, providers)

        return entity
    }

    private fun processMetadataChanged(operation: OperationId, entity: TargetEntity): TargetEntity {
        providers.track.entityProcessingStarted(operation = operation, entity = entity.ref, expectedParts = 0)
        return entity
    }

    private suspend fun pull(
        crates: Map<String, CrateId>,
        entityKey: String
    ): List<Triple<Int, String, suspend () -> Source>> {
        val sources = crates.map { (partPath, crate) ->
            val source = suspend {
                when (val source = providers.clients.core.pull(crate)) {
                    null -> throw RuntimeException("Failed to pull crate [$crate] for entity [$entityKey]")
                    else -> source
                }
            }

            Triple(partIdFromPath(partPath, FileSystems.getDefault()), partPath, source)
        }

        val lastPartId = sources.maxOfOrNull { it.first } ?: 0
        require(lastPartId + 1 == crates.size) {
            "Unexpected last part ID [$lastPartId] encountered for an entity with [${crates.size}] crate(s)"
        }

        return sources
    }

    companion object {
        private val pathPartId: Regex = ".*__part=(\\d+)".toRegex()

        fun partIdFromPath(path: String, fs: FileSystem): Int =
            pathPartId.find(path.extractName(fs))?.groups?.get(1)?.value?.toIntOrNull() ?: 0

        fun expectContentMetadata(entity: TargetEntity): EntityMetadata.WithContent =
            when (val metadata = entity.existingMetadata) {
                is EntityMetadata.File -> metadata
                is EntityMetadata.Library -> metadata
                is EntityMetadata.Directory -> throw IllegalArgumentException(
                    "Expected metadata for file but directory metadata for [${metadata.path}] provided"
                )
            }
    }
}
