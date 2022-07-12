package stasis.client_android.lib.tracking

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.utils.Either
import java.nio.file.Path

interface BackupTracker {
    fun entityDiscovered(operation: OperationId, entity: Path)
    fun specificationProcessed(operation: OperationId, unmatched: List<Pair<Rule, Throwable>>)
    fun entityExamined(operation: OperationId, entity: Path)
    fun entityCollected(operation: OperationId, entity: SourceEntity)
    fun entityProcessingStarted(operation: OperationId,entity: Path, expectedParts: Int)
    fun entityPartProcessed(operation: OperationId,entity: Path)
    fun entityProcessed(operation: OperationId, entity: Path, metadata: Either<EntityMetadata, EntityMetadata>)
    fun metadataCollected(operation: OperationId)
    fun metadataPushed(operation: OperationId, entry: DatasetEntryId)
    fun failureEncountered(operation: OperationId, failure: Throwable)
    fun failureEncountered(operation: OperationId, entity: Path, failure: Throwable)
    fun completed(operation: OperationId)
}
