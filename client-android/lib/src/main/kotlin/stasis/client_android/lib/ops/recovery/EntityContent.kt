package stasis.client_android.lib.ops.recovery

import okio.Source
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.encryption.Decoder
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.ops.recovery.stages.EntityProcessing
import stasis.client_android.lib.ops.recovery.stages.internal.DecompressedSource.decompress
import stasis.client_android.lib.ops.recovery.stages.internal.MergedCrates.merged
import java.nio.file.FileSystems

object EntityContent {
    suspend fun pull(
        metadata: EntityMetadata.WithContent,
        entityKey: String,
        deviceSecret: DeviceSecret,
        clients: Clients,
        decryptor: Decoder,
        onPartProcessed: () -> Unit
    ): Source {
        val crates = metadata.crates
        val filesystem = FileSystems.getDefault()

        val parts = crates.map { (partPath, crate) ->
            val decrypted = suspend {
                val encrypted = when (val source = clients.core.pull(crate)) {
                    null -> throw RuntimeException("Failed to pull crate [$crate] for entity [$entityKey]")
                    else -> source
                }

                decryptor.decrypt(encrypted, deviceSecret.toFileSecret(partPath, metadata.checksum))
            }

            Triple(EntityProcessing.partIdFromPath(partPath, filesystem), partPath, decrypted)
        }

        val lastPartId = parts.maxOfOrNull { it.first } ?: 0
        require(lastPartId + 1 == crates.size) {
            "Unexpected last part ID [$lastPartId] encountered for an entity with [${crates.size}] crate(s)"
        }

        return parts
            .merged(onPartProcessed = onPartProcessed)
            .decompress(decompressor = Compression.fromString(metadata.compression))
    }
}
