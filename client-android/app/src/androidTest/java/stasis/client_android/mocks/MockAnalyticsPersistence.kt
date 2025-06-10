package stasis.client_android.mocks

import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.telemetry.analytics.AnalyticsPersistence
import stasis.client_android.lib.utils.Try
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

open class MockAnalyticsPersistence(private val existing: Try<AnalyticsEntry?>) : AnalyticsPersistence {
    private val cachedEntries: MutableList<AnalyticsEntry> = mutableListOf()
    private val transmittedEntries: MutableList<AnalyticsEntry> = mutableListOf()

    private val lastCachedRef: AtomicReference<Instant> = AtomicReference(Instant.MIN)
    private val lastTransmittedRef: AtomicReference<Instant> = AtomicReference(Instant.MIN)

    override fun cache(entry: AnalyticsEntry) {
        lastCachedRef.set(Instant.now())
        cachedEntries.add(entry)
    }

    override suspend fun transmit(entry: AnalyticsEntry): Try<Unit> {
        lastTransmittedRef.set(Instant.now())
        transmittedEntries.add(entry)
        return Try.Success(Unit)
    }

    override suspend fun restore(): Try<AnalyticsEntry?> =
        existing

    override val lastCached: Instant
        get() = lastCachedRef.get()

    override val lastTransmitted: Instant
        get() = lastTransmittedRef.get()

    val cached: List<AnalyticsEntry>
        get() = cachedEntries

    val transmitted: List<AnalyticsEntry>
        get() = transmittedEntries
}
