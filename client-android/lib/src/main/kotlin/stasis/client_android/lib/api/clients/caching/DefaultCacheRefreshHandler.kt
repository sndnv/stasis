package stasis.client_android.lib.api.clients.caching

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import java.time.Duration
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.coroutines.cancellation.CancellationException

@Suppress("LongParameterList")
open class DefaultCacheRefreshHandler(
    private val underlying: ServerApiEndpointClient,
    private val datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition>,
    private val datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry>,
    private val datasetEntriesForDefinitionCache: Cache<DatasetDefinitionId, DatasetEntriesForDefinition>,
    private val initialDelay: Duration,
    private val activeInterval: Duration,
    private val pendingInterval: Duration,
    coroutineScope: CoroutineScope
) : CacheRefreshHandler {
    private val job: Job

    private val active: Queue<CacheRefreshHandler.RefreshTarget> = ConcurrentLinkedQueue()
    private val pending: Queue<CacheRefreshHandler.RefreshTarget> = ConcurrentLinkedQueue()

    override val stats: CacheRefreshHandler.Statistics = CacheRefreshHandler.Statistics()

    init {
        require(activeInterval > initialDelay) {
            "Initial delay [$initialDelay] must be larger than pending [$pendingInterval] and active [$activeInterval] intervals"
        }

        require(pendingInterval > activeInterval) {
            "Pending interval [$pendingInterval] must be larger than active interval [$activeInterval]"
        }

        job = coroutineScope.launch {
            start()
        }
    }

    override suspend fun refreshNow(target: CacheRefreshHandler.RefreshTarget): Try<Unit> {
        val existingActive = active.remove(target)
        val existingPending = pending.remove(target)
        val result = refreshTarget(target)

        if (existingActive || existingPending) pending.add(target)

        return result
    }

    protected open suspend fun start() {
        active.addAll(CacheRefreshHandler.DefaultTargets)
        delay(timeMillis = initialDelay.toMillis())
        scheduleNext()
    }

    override fun stop() {
        job.cancel()
    }

    private suspend fun refreshTarget(target: CacheRefreshHandler.RefreshTarget): Try<Unit> {
        val result = when (target) {
            is CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions -> {
                underlying.datasetDefinitions().map { refreshed ->
                    datasetDefinitionsCache.clear()
                    datasetDefinitionsCache.put(entries = refreshed.associateBy { it.id })
                }
            }

            is CacheRefreshHandler.RefreshTarget.AllDatasetEntries -> {
                underlying.datasetEntries(definition = target.definition).map { entries ->
                    datasetEntriesCache.put(entries = entries.associateBy { it.id })
                    datasetEntriesForDefinitionCache.put(
                        key = target.definition,
                        value = DatasetEntriesForDefinition(entries = entries)
                    )
                }
            }

            is CacheRefreshHandler.RefreshTarget.LatestDatasetEntry -> {
                underlying.latestEntry(definition = target.definition, until = null).map { entry ->
                    if (entry != null) {
                        datasetEntriesCache.put(entry.id, value = entry)

                        val updated = (datasetEntriesForDefinitionCache.get(key = target.definition)
                            ?: DatasetEntriesForDefinition.empty()).withEntry(entry = entry)

                        datasetEntriesForDefinitionCache.put(key = target.definition, value = updated)
                    } else {
                        datasetEntriesForDefinitionCache.remove(key = target.definition)
                    }
                }
            }

            is CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition -> {
                underlying.datasetDefinition(definition = target.definition).map { definition ->
                    datasetDefinitionsCache.put(key = definition.id, value = definition)
                }
            }

            is CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry -> {
                underlying.datasetEntry(entry = target.entry).map { entry ->
                    datasetEntriesCache.put(key = entry.id, value = entry)

                    val updated = (datasetEntriesForDefinitionCache.get(key = entry.definition)
                        ?: DatasetEntriesForDefinition.empty()).withEntry(entry = entry)

                    datasetEntriesForDefinitionCache.put(key = entry.definition, value = updated)
                }
            }
        }

        stats.withRefreshResult(target, result)

        return result
    }

    private suspend fun scheduleNext(): Unit = withContext(Dispatchers.IO) {
        try {
            when (val next: CacheRefreshHandler.RefreshTarget? = active.poll()) {
                null -> {
                    // all active targets have been processed;
                    // the queues are rotated and will wait until the remaining pending interval passes
                    active.addAll(pending)
                    pending.clear()
                    delay(timeMillis = fuzzyInterval((pendingInterval - activeInterval).toMillis()))
                }

                else -> {
                    when (refreshTarget(target = next)) {
                        is Try.Success -> pending.add(next) // schedule for next interval
                        is Try.Failure -> active.add(next) // schedule for retry
                    }
                    delay(timeMillis = fuzzyInterval(activeInterval.toMillis()))
                }
            }
        } catch (_: CancellationException) {
            // do nothing
        } catch (_: Throwable) {
            if (active.isEmpty() && pending.isEmpty()) {
                active.addAll(CacheRefreshHandler.DefaultTargets)
            }
            delay(timeMillis = fuzzyInterval(activeInterval.toMillis()))
        } finally {
            scheduleNext()
        }
    }

    @Suppress("MagicNumber")
    private fun fuzzyInterval(interval: Long): Long {
        val low = (interval - (interval * 0.02)).toLong()
        val high = (interval + (interval * 0.03)).toLong()

        return ThreadLocalRandom.current().nextLong(low, high)
    }
}
