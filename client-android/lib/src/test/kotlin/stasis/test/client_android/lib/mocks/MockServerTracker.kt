package stasis.test.client_android.lib.mocks

import stasis.client_android.lib.tracking.ServerTracker
import java.util.concurrent.atomic.AtomicInteger

class MockServerTracker : ServerTracker {
    private val stats: Map<Statistic, AtomicInteger> = mapOf(
        Statistic.ServerReachable to AtomicInteger(0),
        Statistic.ServerUnreachable to AtomicInteger(0)
    )

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
