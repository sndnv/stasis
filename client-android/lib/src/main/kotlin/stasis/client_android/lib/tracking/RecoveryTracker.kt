package stasis.client_android.lib.tracking

import stasis.client_android.lib.model.EntityRef
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.state.RecoveryState

interface RecoveryTracker {
    fun started(operation: OperationId)
    fun entityExamined(operation: OperationId, entity: EntityRef, metadataChanged: Boolean, contentChanged: Boolean)
    fun entityCollected(operation: OperationId, entity: TargetEntity)
    fun entityProcessingStarted(operation: OperationId, entity: EntityRef, expectedParts: Int)
    fun entityPartProcessed(operation: OperationId, entity: EntityRef)
    fun entityProcessed(operation: OperationId, entity: EntityRef)
    fun metadataApplied(operation: OperationId, entity: EntityRef)
    fun failureEncountered(operation: OperationId, failure: Throwable)
    fun failureEncountered(operation: OperationId, entity: EntityRef, failure: Throwable)
    fun completed(operation: OperationId)

    suspend fun stateOf(operation: OperationId): RecoveryState?
}
