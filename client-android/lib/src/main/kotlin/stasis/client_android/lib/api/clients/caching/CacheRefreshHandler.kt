package stasis.client_android.lib.api.clients.caching

import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Try
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

interface CacheRefreshHandler {
    suspend fun refreshNow(target: RefreshTarget): Try<Unit>
    fun stop()

    val stats: Statistics

    sealed class RefreshTarget {
        abstract val name: String

        data object AllDatasetDefinitions : RefreshTarget() {
            override val name: String = "all_dataset_definitions"
        }

        data class AllDatasetEntries(val definition: DatasetDefinitionId) : RefreshTarget() {
            override val name: String = "all_dataset_entries"
        }

        data class LatestDatasetEntry(val definition: DatasetDefinitionId) : RefreshTarget() {
            override val name: String = "latest_dataset_entry"
        }

        data class IndividualDatasetDefinition(val definition: DatasetDefinitionId) : RefreshTarget() {
            override val name: String = "individual_dataset_definition"
        }

        data class IndividualDatasetEntry(val entry: DatasetEntryId) : RefreshTarget() {
            override val name: String = "individual_dataset_entry"
        }
    }

    class Statistics {
        private val lastRefreshRef: AtomicReference<Instant?> = AtomicReference(null)

        val targets: Map<String, RefreshTargetStatistic> = mapOf(
            "refreshed_all_dataset_definitions" to RefreshTargetStatistic(),
            "refreshed_all_dataset_entries" to RefreshTargetStatistic(),
            "refreshed_latest_dataset_entry" to RefreshTargetStatistic(),
            "refreshed_individual_dataset_definition" to RefreshTargetStatistic(),
            "refreshed_individual_dataset_entry" to RefreshTargetStatistic(),
        )

        val lastRefresh: Instant?
            get() = lastRefreshRef.get()

        fun withRefreshResult(target: RefreshTarget, result: Try<*>, duration: Long) {
            targets["refreshed_${target.name}"]?.withResult(result, duration)
            lastRefreshRef.set(Instant.now())
        }
    }

    class RefreshTargetStatistic {
        private val successfulRef = AtomicLong(0)
        private val failedRef = AtomicLong(0)
        private val minDurationRef = AtomicLong(Long.MAX_VALUE)
        private val maxDurationRef = AtomicLong(Long.MIN_VALUE)

        fun withResult(result: Try<*>, duration: Long) {
            if (result.isSuccess) {
                successfulRef.incrementAndGet()
            } else {
                failedRef.incrementAndGet()
            }

            minDurationRef.accumulateAndGet(duration) { min, last ->
                if (last > min) min else last
            }

            maxDurationRef.accumulateAndGet(duration) { max, last ->
                if (last < max) max else last
            }
        }

        val successful: Long
            get() = successfulRef.get()

        val failed: Long
            get() = failedRef.get()

        val minDuration: Long
            get() = minDurationRef.get()

        val maxDuration: Long
            get() = maxDurationRef.get()
    }

    companion object {
        val DefaultTargets: List<RefreshTarget> = listOf(
            RefreshTarget.AllDatasetDefinitions
        )
    }
}

