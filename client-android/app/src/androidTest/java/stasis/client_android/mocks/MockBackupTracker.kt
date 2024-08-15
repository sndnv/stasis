package stasis.client_android.mocks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.BackupTracker
import stasis.client_android.lib.tracking.state.BackupState
import stasis.client_android.lib.utils.Either
import stasis.client_android.tracking.BackupTrackerManage
import stasis.client_android.tracking.BackupTrackerView
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class MockBackupTracker : BackupTracker, BackupTrackerView, BackupTrackerManage {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.Started to AtomicInteger(0),
        Statistic.EntityDiscovered to AtomicInteger(0),
        Statistic.SpecificationProcessed to AtomicInteger(0),
        Statistic.EntityExamined to AtomicInteger(0),
        Statistic.EntitySkipped to AtomicInteger(0),
        Statistic.EntityCollected to AtomicInteger(0),
        Statistic.EntityProcessingStarted to AtomicInteger(0),
        Statistic.EntityPartProcessed to AtomicInteger(0),
        Statistic.EntityProcessed to AtomicInteger(0),
        Statistic.MetadataCollected to AtomicInteger(0),
        Statistic.MetadataPushed to AtomicInteger(0),
        Statistic.FailureEncountered to AtomicInteger(0),
        Statistic.Completed to AtomicInteger(0)
    )

    override val state: LiveData<Map<OperationId, BackupState>>
        get() = MutableLiveData(emptyMap())

    override fun updates(operation: OperationId): LiveData<BackupState> =
        MutableLiveData(BackupState.start(operation, UUID.randomUUID()))

    override fun remove(operation: OperationId) {}

    override fun clear() {}

    override suspend fun stateOf(operation: OperationId): BackupState? =
        null

    override fun started(operation: OperationId, definition: DatasetDefinitionId) {
        stats[Statistic.Started]?.getAndIncrement()
    }

    override fun entityDiscovered(
        operation: OperationId,
        entity: Path
    ) {
        stats[Statistic.EntityDiscovered]?.getAndIncrement()
    }

    override fun specificationProcessed(
        operation: OperationId,
        unmatched: List<Pair<Rule, Throwable>>
    ) {
        stats[Statistic.SpecificationProcessed]?.getAndIncrement()
    }

    override fun entityExamined(operation: OperationId, entity: Path) {
        stats[Statistic.EntityExamined]?.getAndIncrement()
    }

    override fun entitySkipped(operation: OperationId, entity: Path) {
        stats[Statistic.EntitySkipped]?.getAndIncrement()
    }

    override fun entityCollected(operation: OperationId, entity: SourceEntity) {
        stats[Statistic.EntityCollected]?.getAndIncrement()
    }

    override fun entityProcessingStarted(operation: OperationId, entity: Path, expectedParts: Int) {
        stats[Statistic.EntityProcessingStarted]?.getAndIncrement()
    }

    override fun entityPartProcessed(operation: OperationId, entity: Path) {
        stats[Statistic.EntityPartProcessed]?.getAndIncrement()
    }

    override fun entityProcessed(
        operation: OperationId,
        entity: Path,
        metadata: Either<EntityMetadata, EntityMetadata>
    ) {
        stats[Statistic.EntityProcessed]?.getAndIncrement()
    }

    override fun metadataCollected(operation: OperationId) {
        stats[Statistic.MetadataCollected]?.getAndIncrement()
    }

    override fun metadataPushed(operation: OperationId, entry: DatasetEntryId) {
        stats[Statistic.MetadataPushed]?.getAndIncrement()
    }

    override fun failureEncountered(operation: OperationId, entity: Path, failure: Throwable) {
        stats[Statistic.FailureEncountered]?.getAndIncrement()
    }

    override fun failureEncountered(operation: OperationId, failure: Throwable) {
        stats[Statistic.FailureEncountered]?.getAndIncrement()
    }

    override fun completed(operation: OperationId) {
        stats[Statistic.Completed]?.getAndIncrement()
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object Started : Statistic()
        object EntityDiscovered : Statistic()
        object SpecificationProcessed : Statistic()
        object EntitySkipped : Statistic()
        object EntityExamined : Statistic()
        object EntityCollected : Statistic()
        object EntityProcessingStarted : Statistic()
        object EntityPartProcessed : Statistic()
        object EntityProcessed : Statistic()
        object MetadataCollected : Statistic()
        object MetadataPushed : Statistic()
        object FailureEncountered : Statistic()
        object Completed : Statistic()
    }
}
