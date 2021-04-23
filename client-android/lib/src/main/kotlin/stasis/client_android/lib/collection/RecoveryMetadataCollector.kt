package stasis.client_android.lib.collection

import stasis.client_android.lib.analysis.Checksum
import stasis.client_android.lib.analysis.Metadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.TargetEntity
import java.nio.file.Path

interface RecoveryMetadataCollector {
    suspend fun collect(
        entity: Path,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ): TargetEntity

    class Default(private val checksum: Checksum) : RecoveryMetadataCollector {
        override suspend fun collect(
            entity: Path,
            destination: TargetEntity.Destination,
            existingMetadata: EntityMetadata
        ): TargetEntity =
            Metadata.collectTarget(
                checksum = checksum,
                entity = entity,
                destination = destination,
                existingMetadata = existingMetadata
            )
    }
}
