package stasis.client_android.lib.ops.scheduling

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.recovery.RecoverySourceKind
import java.nio.file.Path
import java.time.Instant

interface OperationExecutor {
    suspend fun active(): Map<OperationId, Operation.Type>
    suspend fun completed(): Map<OperationId, Operation.Type>
    suspend fun find(operation: OperationId): Operation.Type?

    suspend fun startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: List<Rule>,
        f: (Throwable?) -> Unit
    ): OperationId

    suspend fun startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: List<Path>,
        f: (Throwable?) -> Unit
    ): OperationId

    suspend fun resumeBackup(
        operation: OperationId,
        f: (Throwable?) -> Unit
    ): OperationId

    suspend fun startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Instant?,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        f: (Throwable?) -> Unit
    ): OperationId

    suspend fun startRecoveryWithEntry(
        entry: DatasetEntryId,
        entities: Set<String>?,
        sources: Set<RecoverySourceKind>,
        destination: Recovery.Destination?,
        f: (Throwable?) -> Unit
    ): OperationId

    suspend fun startExpiration(f: (Throwable?) -> Unit): OperationId

    suspend fun startValidation(f: (Throwable?) -> Unit): OperationId

    suspend fun startKeyRotation(f: (Throwable?) -> Unit): OperationId

    suspend fun stop(operation: OperationId)
}
