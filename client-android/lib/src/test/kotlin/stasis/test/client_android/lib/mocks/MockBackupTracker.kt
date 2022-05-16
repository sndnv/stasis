package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.collection.rules.Rule
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.tracking.BackupTracker
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class MockBackupTracker : BackupTracker {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.EntityDiscovered to AtomicInteger(0),
        Statistic.SpecificationProcessed to AtomicInteger(0),
        Statistic.EntityExamined to AtomicInteger(0),
        Statistic.EntityCollected to AtomicInteger(0),
        Statistic.EntityProcessed to AtomicInteger(0),
        Statistic.MetadataCollected to AtomicInteger(0),
        Statistic.MetadataPushed to AtomicInteger(0),
        Statistic.FailureEncountered to AtomicInteger(0),
        Statistic.Completed to AtomicInteger(0)
    )

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

    override fun entityExamined(
        operation: OperationId,
        entity: Path,
        metadataChanged: Boolean,
        contentChanged: Boolean
    ) {
        stats[Statistic.EntityExamined]?.getAndIncrement()
    }

    override fun entityCollected(operation: OperationId, entity: Path) {
        stats[Statistic.EntityCollected]?.getAndIncrement()
    }

    override fun entityProcessed(operation: OperationId, entity: Path, contentChanged: Boolean) {
        stats[Statistic.EntityProcessed]?.getAndIncrement()
    }

    override fun metadataCollected(operation: OperationId) {
        stats[Statistic.MetadataCollected]?.getAndIncrement()
    }

    override fun metadataPushed(operation: OperationId, entry: DatasetEntryId) {
        stats[Statistic.MetadataPushed]?.getAndIncrement()
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
        object EntityDiscovered : Statistic()
        object SpecificationProcessed : Statistic()
        object EntityExamined : Statistic()
        object EntityCollected : Statistic()
        object EntityProcessed : Statistic()
        object MetadataCollected : Statistic()
        object MetadataPushed : Statistic()
        object FailureEncountered : Statistic()
        object Completed : Statistic()
    }
}
