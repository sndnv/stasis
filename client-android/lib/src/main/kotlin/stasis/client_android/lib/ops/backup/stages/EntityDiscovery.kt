package stasis.client_android.lib.ops.backup.stages

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import stasis.client_android.lib.collection.BackupCollector
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.collection.rules.exceptions.RuleParsingFailure
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.backup.BackupEntityKind
import stasis.client_android.lib.ops.backup.Providers
import stasis.client_android.lib.tracking.state.BackupState
import java.nio.file.Path

interface EntityDiscovery {
    val collector: Collector
    val latestMetadata: DatasetMetadata?
    val providers: Providers

    fun entityDiscovery(operation: OperationId): Flow<BackupCollector> =
        flow {
            reportUnsupportedSources(operation)

            providers.kinds.forEach { kind ->
                emit(kind.collector(operation, collector, latestMetadata, providers))
            }
        }

    private fun reportUnsupportedSources(operation: OperationId) {
        val coll = collector
        if (coll is Collector.WithRules) {
            val handledSchemes: Set<String?> =
                providers.kinds.map { (it as? BackupEntityKind.Library)?.scheme }.toSet()

            coll.rules.forEach { rule ->
                if (!handledSchemes.contains(SourceUri.scheme(rule.source))) {
                    providers.track.failureEncountered(
                        operation,
                        RuleParsingFailure("No backup kind was registered for source [${rule.source}]")
                    )
                }
            }
        }
    }

    sealed class Collector {
        data class WithRules(val rules: List<Rule>) : Collector()
        data class WithEntities(val entities: List<Path>) : Collector()
        data class WithState(val state: BackupState) : Collector()
    }
}
