package stasis.client_android.activities.helpers

import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.recovery.RecoverySourceKind
import stasis.client_android.lib.ops.scheduling.OperationExecutor
import java.time.Instant

data class RecoveryConfig(
    val definition: DatasetDefinitionId?,
    val recoverySource: RecoverySource,
    val sources: Set<RecoverySourceKind>,
    val destination: String?,
    val discardPaths: Boolean
) {
    fun validate(): ValidationResult =
        when {
            definition == null -> ValidationResult.MissingDefinition
            sources.isEmpty() -> ValidationResult.MissingSources
            else -> when (val source = recoverySource) {
                is RecoverySource.Latest -> ValidationResult.Valid
                is RecoverySource.Entry -> if (source.entry != null) {
                    ValidationResult.Valid
                } else {
                    ValidationResult.MissingEntry
                }

                is RecoverySource.Until -> ValidationResult.Valid
            }
        }

    suspend fun startRecovery(withExecutor: OperationExecutor, f: (Throwable?) -> Unit) {
        when (val source = recoverySource) {
            is RecoverySource.Latest -> {
                withExecutor.startRecoveryWithDefinition(
                    definition = recoveryDefinition,
                    until = null,
                    entities = null,
                    sources = recoverySources,
                    destination = recoveryDestination,
                    f = f
                )
            }

            is RecoverySource.Entry -> {
                withExecutor.startRecoveryWithEntry(
                    entry = recoveryEntry,
                    entities = null,
                    sources = recoverySources,
                    destination = recoveryDestination,
                    f = f
                )
            }

            is RecoverySource.Until -> {
                withExecutor.startRecoveryWithDefinition(
                    definition = recoveryDefinition,
                    until = source.instant,
                    entities = null,
                    sources = recoverySources,
                    destination = recoveryDestination,
                    f = f
                )
            }
        }
    }

    val recoveryDefinition: DatasetDefinitionId
        get() {
            require(definition != null) { "Unexpected empty definition encountered" }
            return definition
        }

    val recoveryEntry: DatasetEntryId
        get() {
            when (val source = recoverySource) {
                is RecoverySource.Entry -> {
                    require(source.entry != null) { "Unexpected empty source entry encountered" }
                    return source.entry
                }

                else -> throw IllegalArgumentException("Unexpected recovery source encountered: [${source.javaClass.simpleName}]")
            }
        }

    val recoveryDestination: Recovery.Destination?
        get() = destination?.let {
            Recovery.Destination(
                path = it,
                keepStructure = !discardPaths,
                preserveExisting = false
            )
        }

    val recoverySources: Set<RecoverySourceKind>
        get() = sources

    sealed class RecoverySource {
        object Latest : RecoverySource()
        data class Entry(val entry: DatasetEntryId?) : RecoverySource()
        data class Until(val instant: Instant) : RecoverySource()
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        object MissingDefinition : ValidationResult()
        object MissingEntry : ValidationResult()
        object MissingSources : ValidationResult()
    }
}
