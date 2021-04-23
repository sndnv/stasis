package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.RecoveryTracker
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MockRecoveryTracker : RecoveryTracker {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.EntityExamined to AtomicInteger(0),
        Statistic.EntityCollected to AtomicInteger(0),
        Statistic.EntityProcessed to AtomicInteger(0),
        Statistic.MetadataApplied to AtomicInteger(0),
        Statistic.FailureEncountered to AtomicInteger(0),
        Statistic.Completed to AtomicInteger(0)
    )

    override fun entityExamined(operation: OperationId, entity: Path, metadataChanged: Boolean, contentChanged: Boolean) {
        stats[Statistic.EntityExamined]?.getAndIncrement()
    }

    override fun entityCollected(operation: OperationId, entity: Path) {
        stats[Statistic.EntityCollected]?.getAndIncrement()
    }

    override fun entityProcessed(operation: OperationId, entity: Path) {
        stats[Statistic.EntityProcessed]?.getAndIncrement()
    }

    override fun metadataApplied(operation: OperationId, entity: Path) {
        stats[Statistic.MetadataApplied]?.getAndIncrement()
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
        object EntityProcessed : Statistic()
        object MetadataApplied : Statistic()
        object FailureEncountered : Statistic()
        object Completed : Statistic()
    }
}
