package stasis.client_android.mocks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.tracking.ServerTracker
import stasis.client_android.tracking.ServerTrackerView
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class MockServerTracker : ServerTracker, ServerTrackerView {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.ServerReachable to AtomicInteger(0),
        Statistic.ServerUnreachable to AtomicInteger(0)
    )

    override val state: LiveData<Map<String, ServerTracker.ServerState>>
        get() = MutableLiveData(emptyMap())

    override fun updates(server: String): LiveData<ServerTracker.ServerState> =
        MutableLiveData(ServerTracker.ServerState(reachable = false, timestamp = Instant.now()))

    override fun reachable(server: String) {
        stats[Statistic.ServerReachable]?.getAndIncrement()
    }

    override fun unreachable(server: String) {
        stats[Statistic.ServerUnreachable]?.getAndIncrement()
    }

    val statistics: Map<Statistic, Int>
        get() = stats.mapValues { it.value.get() }

    sealed class Statistic {
        object ServerReachable : Statistic()
        object ServerUnreachable : Statistic()
    }
}
