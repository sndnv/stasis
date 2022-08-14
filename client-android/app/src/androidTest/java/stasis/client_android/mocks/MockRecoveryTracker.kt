package stasis.client_android.mocks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.RecoveryTracker
import stasis.client_android.lib.tracking.state.RecoveryState
import stasis.client_android.tracking.RecoveryTrackerManage
import stasis.client_android.tracking.RecoveryTrackerView
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MockRecoveryTracker : RecoveryTracker, RecoveryTrackerView, RecoveryTrackerManage {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.EntityExamined to AtomicInteger(0),
        Statistic.EntityCollected to AtomicInteger(0),
        Statistic.EntityProcessingStarted to AtomicInteger(0),
        Statistic.EntityPartProcessed to AtomicInteger(0),
        Statistic.EntityProcessed to AtomicInteger(0),
        Statistic.MetadataApplied to AtomicInteger(0),
        Statistic.FailureEncountered to AtomicInteger(0),
        Statistic.Completed to AtomicInteger(0)
    )

    override val state: LiveData<Map<OperationId, RecoveryState>>
        get() = MutableLiveData(emptyMap())

    override fun updates(operation: OperationId): LiveData<RecoveryState> =
        MutableLiveData(RecoveryState.start(operation))

    override suspend fun stateOf(operation: OperationId): RecoveryState? =
        null

    override fun remove(operation: OperationId) {}

    override fun clear() {}

    override fun entityExamined(
        operation: OperationId,
        entity: Path,
        metadataChanged: Boolean,
        contentChanged: Boolean
    ) {
        stats[Statistic.EntityExamined]?.getAndIncrement()
    }

    override fun entityCollected(operation: OperationId, entity: TargetEntity) {
        stats[Statistic.EntityCollected]?.getAndIncrement()
    }

    override fun entityProcessingStarted(operation: OperationId, entity: Path, expectedParts: Int) {
        stats[Statistic.EntityProcessingStarted]?.getAndIncrement()
    }

    override fun entityPartProcessed(operation: OperationId, entity: Path) {
        stats[Statistic.EntityPartProcessed]?.getAndIncrement()
    }

    override fun entityProcessed(operation: OperationId, entity: Path) {
        stats[Statistic.EntityProcessed]?.getAndIncrement()
    }

    override fun metadataApplied(operation: OperationId, entity: Path) {
        stats[Statistic.MetadataApplied]?.getAndIncrement()
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
        object EntityExamined : Statistic()
        object EntityCollected : Statistic()
        object EntityProcessingStarted : Statistic()
        object EntityPartProcessed : Statistic()
        object EntityProcessed : Statistic()
        object MetadataApplied : Statistic()
        object FailureEncountered : Statistic()
        object Completed : Statistic()
    }
}
