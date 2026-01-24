package stasis.client_android.lib.ops.recovery.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
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
import stasis.client_android.lib.ops.recovery.stages.internal.DecompressedSource.decompress
import stasis.client_android.lib.ops.recovery.stages.internal.DecryptedCrates.decrypt
import stasis.client_android.lib.ops.recovery.stages.internal.DestagedByteStringSource.destage
import stasis.client_android.lib.ops.recovery.stages.internal.MergedCrates.merged
import stasis.client_android.lib.utils.NonFatal.nonFatal
import stasis.client_android.lib.utils.StringPaths.extractName
import stasis.client_android.lib.utils.Try
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

interface EntityProcessing {
    val deviceSecret: DeviceSecret
    val providers: Providers

    private val targetDirectoryPermissions: String
        get() = "rwx------"

    private val targetDirectoryAttributes: FileAttribute<Set<PosixFilePermission>>
        get() = PosixFilePermissions.asFileAttribute(
            PosixFilePermissions.fromString(
                targetDirectoryPermissions
            )
        )

    fun entityProcessing(operation: OperationId, flow: Flow<TargetEntity>): Flow<TargetEntity> =
        flow.map {
            when (it.destination) {
                is TargetEntity.Destination.Default -> createEntityDirectory(it)
                is TargetEntity.Destination.Directory -> when {
                    it.destination.keepDefaultStructure -> createEntityDirectory(it)
                    it.existingMetadata is EntityMetadata.File -> it
                    else -> null
                }
            }
        }.filterNotNull()
            .map { createEntityDirectory(it) }
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
                            entity = entity.destinationPath
                        )
                        entity
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

    private fun createEntityDirectory(entity: TargetEntity): TargetEntity {
        val entityDirectory = when (entity.existingMetadata) {
            is EntityMetadata.File -> entity.destinationPath.parent
            is EntityMetadata.Directory -> entity.destinationPath
        }

        Files.createDirectories(entityDirectory, targetDirectoryAttributes)

        return entity
    }

    private suspend fun processContentChanged(operation: OperationId, entity: TargetEntity): TargetEntity {
        val file = expectFileMetadata(entity)
        val crates = file.crates

        providers.track.entityProcessingStarted(
            operation = operation,
            entity = entity.path,
            expectedParts = crates.size
        )

        fun recordPartProcessed(): Unit =
            providers.track.entityPartProcessed(operation, entity = entity.path)

        pull(crates, entity.originalPath)
            .decrypt(withPartSecret = { deviceSecret.toFileSecret(it, file.checksum) }, providers = providers)
            .merged(onPartProcessed = ::recordPartProcessed)
            .decompress(decompressor = providers.compression.decoderFor(entity))
            .destage(to = entity.destinationPath, providers = providers)

        return entity
    }

    private fun processMetadataChanged(operation: OperationId, entity: TargetEntity): TargetEntity {
        providers.track.entityProcessingStarted(operation = operation, entity = entity.path, expectedParts = 0)
        return entity
    }

    private suspend fun pull(crates: Map<String, CrateId>, entity: Path): List<Triple<Int, String, suspend () -> Source>> {
        val sources = crates.map { (partPath, crate) ->
            val source = suspend {
                when (val source = providers.clients.core.pull(crate)) {
                    null -> throw RuntimeException("Failed to pull crate [$crate] for entity [$entity]")
                    else -> source
                }
            }

            Triple(partIdFromPath(partPath, entity.fileSystem), partPath, source)
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

        fun expectFileMetadata(entity: TargetEntity): EntityMetadata.File {
            return when (entity.existingMetadata) {
                is EntityMetadata.File -> entity.existingMetadata
                is EntityMetadata.Directory -> throw IllegalArgumentException(
                    "Expected metadata for file but directory metadata for [${entity.existingMetadata.path}] provided"
                )
            }
        }
    }
}
