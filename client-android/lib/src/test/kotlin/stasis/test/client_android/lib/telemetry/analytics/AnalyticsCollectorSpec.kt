package stasis.test.client_android.lib.telemetry.analytics

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.client_android.lib.utils.Try

class AnalyticsCollectorSpec : WordSpec({
    "A NoOp AnalyticsCollector" should {
        "record nothing" {
            val collector = AnalyticsCollector.NoOp

            collector.recordEvent("test_event")
            collector.recordEvent("test_event", result = Try.Success(Unit))
            collector.recordEvent("test_event", "a" to "b")
            collector.recordEvent("test_event", "a" to "b", "c" to "d")
            collector.recordEvent("test_event", mapOf("a" to "b"))

            collector.recordFailure(RuntimeException("Test failure"))
            collector.recordFailure("Other failure")

            collector.persistence shouldBe (null)

            val state = collector.state().get()
            state.events.isEmpty() shouldBe (true)
            state.failures.isEmpty() shouldBe (true)
        }
    }
})
