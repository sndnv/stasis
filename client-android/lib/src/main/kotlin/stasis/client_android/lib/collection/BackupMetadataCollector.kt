package stasis.client_android.lib.collection

import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.compression.Compression
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import java.nio.file.Path

interface BackupMetadataCollector {
    suspend fun collect(entity: Path, existingMetadata: EntityMetadata?): SourceEntity

    class Default(private val checksum: Checksum, private val compression: Compression) : BackupMetadataCollector {
        override suspend fun collect(entity: Path, existingMetadata: EntityMetadata?): SourceEntity =
            Metadata.collectSource(
                checksum = checksum,
                compression = compression,
                entity = entity, existingMetadata = existingMetadata
            )
    }
}
