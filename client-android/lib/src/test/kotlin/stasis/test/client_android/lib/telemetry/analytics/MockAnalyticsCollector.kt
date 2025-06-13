package stasis.test.client_android.lib.telemetry.analytics

import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.telemetry.analytics.AnalyticsPersistence
import stasis.client_android.lib.utils.Try
import java.util.concurrent.atomic.AtomicReference

class MockAnalyticsCollector : AnalyticsCollector {
    private val entryRef: AtomicReference<AnalyticsEntry.Collected> =
        AtomicReference(AnalyticsEntry.collected(app = ApplicationInformation.none()))

    override fun recordEvent(name: String, attributes: Map<String, String>) {
        entryRef.updateAndGet { it.withEvent(name, attributes) }
    }

    override fun recordFailure(message: String) {
        entryRef.updateAndGet { it.withFailure(message) }
    }

    override fun state(): Try<AnalyticsEntry> =
        Try.Success(entryRef.get())

    override fun send() = Unit

    override val persistence: AnalyticsPersistence? =
        null
}
