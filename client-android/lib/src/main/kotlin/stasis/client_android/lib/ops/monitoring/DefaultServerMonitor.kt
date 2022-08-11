package stasis.client_android.lib.ops.monitoring

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.tracking.ServerTracker
import java.lang.Long.max
import java.time.Duration

class DefaultServerMonitor(
    private val initialDelay: Duration,
    private val interval: Duration,
    private val api: ServerApiEndpointClient,
    private val tracker: ServerTracker,
    scope: CoroutineScope
) : ServerMonitor {
    private val job: Job

    init {
        job = scope.launch {
            delay(timeMillis = initialDelay.toMillis())
            scheduleNextPing()
        }
    }

    private suspend fun scheduleNextPing(): Unit = withContext(Dispatchers.IO) {
        try {
            api.ping().get()
            tracker.reachable(api.server)
            delay(timeMillis = interval.toMillis())
        } catch (_: CancellationException) {
            // do nothing
        } catch (e: Throwable) {
            tracker.unreachable(api.server)
            delay(timeMillis = max(interval.toMillis() / UnreachableIntervalReduction, initialDelay.toMillis()))
        } finally {
            scheduleNextPing()
        }
    }

    override suspend fun stop() {
        job.cancel()
    }

    companion object {
        const val UnreachableIntervalReduction: Int = 10
    }
}
