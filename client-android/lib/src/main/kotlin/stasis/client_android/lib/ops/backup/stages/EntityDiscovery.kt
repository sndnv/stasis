package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.BackupMetadataCollector
import stasis.client_android.lib.collection.DefaultBackupCollector
import stasis.client_android.lib.collection.rules.Rule

import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Providers
import java.nio.file.Files
import java.nio.file.Path

interface EntityDiscovery {
    val collector: Collector
    val latestMetadata: DatasetMetadata?
    val providers: Providers

    fun entityDiscovery(operation: OperationId): Flow<BackupCollector> {
        val discovered = when (val coll = collector) {
            is Collector.WithRules -> {
                listOf(Specification.tracked(operation, coll.rules, providers.track))
                    .asFlow()
                    .map { spec ->
                        spec.includedParents.forEach { providers.track.entityDiscovered(operation, it) }
                        providers.track.specificationProcessed(operation, unmatched = spec.unmatched)
                        spec.included
                    }
            }

            is Collector.WithEntities -> {
                listOf(coll.entities)
                    .asFlow()
                    .map { entities ->
                        val existing = entities.filter { Files.exists(it) }
                        existing.forEach { providers.track.entityDiscovered(operation, it) }
                        existing
                    }
            }
        }

        return discovered
            .map { entities ->
                DefaultBackupCollector(
                    entities = entities,
                    latestMetadata = latestMetadata,
                    metadataCollector = BackupMetadataCollector.Default(checksum = providers.checksum),
                    api = providers.clients.api
                )
            }
    }


    sealed class Collector {
        data class WithRules(val rules: List<Rule>) : Collector()
        data class WithEntities(val entities: List<Path>) : Collector()
    }
}
