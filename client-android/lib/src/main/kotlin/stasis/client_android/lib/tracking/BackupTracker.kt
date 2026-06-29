package stasis.client_android.lib.tracking

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Either

interface BackupTracker {
    fun started(operation: OperationId, definition: DatasetDefinitionId)
    fun entityDiscovered(operation: OperationId, entity: EntityRef)
    fun specificationProcessed(operation: OperationId, unmatched: List<Pair<Rule, Throwable>>)
    fun entityExamined(operation: OperationId, entity: EntityRef)
    fun entitySkipped(operation: OperationId, entity: EntityRef)
    fun entityCollected(operation: OperationId, entity: SourceEntity)
    fun entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int)
    fun entityPartProcessed(operation: OperationId, entity: EntityRef)
    fun entityProcessed(operation: OperationId, entity: EntityRef, metadata: Either<EntityMetadata, EntityMetadata>)
    fun metadataCollected(operation: OperationId)
    fun metadataPushed(operation: OperationId, entry: DatasetEntryId)
    fun failureEncountered(operation: OperationId, failure: Throwable)
    fun failureEncountered(operation: OperationId, entity: EntityRef, failure: Throwable)
    fun completed(operation: OperationId)

    suspend fun stateOf(operation: OperationId): BackupState?
}
