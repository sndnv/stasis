package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.collection.RecoveryMetadataCollector
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.TargetEntity
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MockRecoveryMetadataCollector(val metadata: Map<Path, EntityMetadata>) : RecoveryMetadataCollector {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.FileCollectedWithExistingMetadata to AtomicInteger(0)
    )

    override suspend fun collect(
        entity: Path,
        destination: TargetEntity.Destination,
        existingMetadata: EntityMetadata
    ): TargetEntity {
        stats[Statistic.FileCollectedWithExistingMetadata]?.getAndIncrement()

        return when (val fileMetadata = metadata[entity]) {
            null -> throw IllegalArgumentException("No metadata found for file [$entity]")
            else -> TargetEntity(
                path = entity,
                destination = destination,
                existingMetadata = existingMetadata,
                currentMetadata = fileMetadata
            )
        }
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object FileCollectedWithExistingMetadata : Statistic()
    }
}
