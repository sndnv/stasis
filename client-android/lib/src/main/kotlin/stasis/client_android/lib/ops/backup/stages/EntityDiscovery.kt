package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.BackupMetadataCollector
import stasis.client_android.lib.collection.DefaultBackupCollector
import stasis.client_android.lib.collection.rules.Rule

import stasis.client_android.lib.collection.rules.Specification
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.tracking.state.BackupState
import java.nio.file.Files
import java.nio.file.Path

interface EntityDiscovery {
    val collector: Collector
    val latestMetadata: DatasetMetadata?
    val providers: Providers

    fun entityDiscovery(operation: OperationId): Flow<BackupCollector> {
        val discovered = flow {
            when (val coll = collector) {
                is Collector.WithRules -> {
                    val spec = Specification.tracked(operation, coll.rules, providers.track)

                    spec.includedParents.forEach { providers.track.entityDiscovered(operation, it) }
                    providers.track.specificationProcessed(operation, unmatched = spec.unmatched)
                    emit(spec.included)
                }

                is Collector.WithEntities -> {
                    val existing = coll.entities.filter { Files.exists(it) }
                    existing.forEach { providers.track.entityDiscovered(operation, it) }
                    emit(existing)
                }

                is Collector.WithState -> {
                    emit(coll.state.remainingEntities())
                }
            }
        }

        return discovered
            .map { entities ->
                DefaultBackupCollector(
                    entities = entities,
                    latestMetadata = latestMetadata,
                    metadataCollector = BackupMetadataCollector.Default(
                        checksum = providers.checksum,
                        compression = providers.compression
                    ),
                    api = providers.clients.api
                )
            }
    }


    sealed class Collector {
        data class WithRules(val rules: List<Rule>) : Collector()
        data class WithEntities(val entities: List<Path>) : Collector()
        data class WithState(val state: BackupState) : Collector()
    }
}
