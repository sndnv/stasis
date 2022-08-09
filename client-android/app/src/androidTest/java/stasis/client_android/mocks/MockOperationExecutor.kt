package stasis.client_android.mocks

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.ops.recovery.Recovery
import stasis.client_android.lib.ops.scheduling.OperationExecutor
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class MockOperationExecutor : OperationExecutor {
    override suspend fun active(): Map<OperationId, Operation.Type> = emptyMap()
    override suspend fun completed(): Map<OperationId, Operation.Type> = emptyMap()
    override suspend fun find(operation: OperationId): Operation.Type? = null

    override suspend fun startBackupWithRules(
        definition: DatasetDefinitionId,
        rules: List<Rule>,
        f: (Throwable?) -> Unit
    ): OperationId = UUID.randomUUID()

    override suspend fun startBackupWithEntities(
        definition: DatasetDefinitionId,
        entities: List<Path>,
        f: (Throwable?) -> Unit
    ): OperationId = UUID.randomUUID()

    override suspend fun resumeBackup(
        operation: OperationId,
        f: (Throwable?) -> Unit
    ): OperationId = UUID.randomUUID()

    override suspend fun startRecoveryWithDefinition(
        definition: DatasetDefinitionId,
        until: Instant?,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        f: (Throwable?) -> Unit
    ): OperationId = UUID.randomUUID()

    override suspend fun startRecoveryWithEntry(
        entry: DatasetEntryId,
        query: Recovery.PathQuery?,
        destination: Recovery.Destination?,
        f: (Throwable?) -> Unit
    ): OperationId = UUID.randomUUID()

    override suspend fun startExpiration(f: (Throwable?) -> Unit): OperationId = UUID.randomUUID()

    override suspend fun startValidation(f: (Throwable?) -> Unit): OperationId = UUID.randomUUID()

    override suspend fun startKeyRotation(f: (Throwable?) -> Unit): OperationId = UUID.randomUUID()

    override suspend fun stop(operation: OperationId) = Unit
}
