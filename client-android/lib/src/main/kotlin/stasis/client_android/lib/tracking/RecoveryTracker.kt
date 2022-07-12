package stasis.client_android.lib.tracking

import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import java.nio.file.Path

interface RecoveryTracker {
    fun entityExamined(operation: OperationId, entity: Path, metadataChanged: Boolean, contentChanged: Boolean)
    fun entityCollected(operation: OperationId, entity: TargetEntity)
    fun entityProcessingStarted(operation: OperationId, entity: Path, expectedParts: Int)
    fun entityPartProcessed(operation: OperationId, entity: Path)
    fun entityProcessed(operation: OperationId, entity: Path)
    fun metadataApplied(operation: OperationId, entity: Path)
    fun failureEncountered(operation: OperationId, failure: Throwable)
    fun failureEncountered(operation: OperationId, entity: Path, failure: Throwable)
    fun completed(operation: OperationId)
}
