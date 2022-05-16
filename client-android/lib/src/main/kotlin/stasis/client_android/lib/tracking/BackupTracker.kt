package stasis.client_android.lib.tracking

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import java.nio.file.Path

interface BackupTracker {
    fun entityDiscovered(operation: OperationId, entity: Path)
    fun specificationProcessed(operation: OperationId, unmatched: List<Pair<Rule, Throwable>>)
    fun entityExamined(operation: OperationId, entity: Path, metadataChanged: Boolean, contentChanged: Boolean)
    fun entityCollected(operation: OperationId, entity: Path)
    fun entityProcessed(operation: OperationId, entity: Path, contentChanged: Boolean)
    fun metadataCollected(operation: OperationId)
    fun metadataPushed(operation: OperationId, entry: DatasetEntryId)
    fun failureEncountered(operation: OperationId, failure: Throwable)
    fun completed(operation: OperationId)
}
