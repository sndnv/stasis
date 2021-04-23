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
import java.time.Duration

class DefaultServerMonitor(
    private val interval: Duration,
    private val api: ServerApiEndpointClient,
    private val tracker: ServerTracker,
    scope: CoroutineScope
) : ServerMonitor {
    private val job: Job

    init {
        job = scope.launch { scheduleNextPing() }
    }

    private suspend fun scheduleNextPing(): Unit = withContext(Dispatchers.IO) {
        try {
            delay(timeMillis = interval.toMillis())
            api.ping()
            tracker.reachable(api.server)
        } catch (_: CancellationException) {
            // do nothing
        } catch (_: Throwable) {
            tracker.unreachable(api.server)
        } finally {
            scheduleNextPing()
        }
    }

    override suspend fun stop() {
        job.cancel()
    }
}
