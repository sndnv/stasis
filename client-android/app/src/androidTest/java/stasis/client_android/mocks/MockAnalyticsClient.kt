package stasis.client_android.mocks

import stasis.client_android.lib.telemetry.analytics.AnalyticsClient
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MockAnalyticsClient(private val result: Try<Unit>) : AnalyticsClient {

    private val sentRef: AtomicInteger = AtomicInteger(0)

    private val lastEntryRef: AtomicReference<AnalyticsEntry?> = AtomicReference(null)

    override suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit> =
        result.map {
            sentRef.incrementAndGet()
            lastEntryRef.set(entry)
        }

    val sent: Int
        get() = sentRef.get()

    val lastEntry: AnalyticsEntry?
        get() = lastEntryRef.get()

    companion object {
        operator fun invoke(): MockAnalyticsClient =
            MockAnalyticsClient(result = Try.Success(Unit))
    }
}
