package stasis.test.client_android.lib.telemetry.analytics

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import stasis.client_android.lib.telemetry.analytics.AnalyticsCollector
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.telemetry.analytics.AnalyticsPersistence
import stasis.client_android.lib.utils.Try
import java.util.concurrent.atomic.AtomicReference

class AnalyticsCollectorSpec : WordSpec({
    "An AnalyticsCollector" should {
        "record failure stack traces" {
            val recordedStackTrace = AtomicReference<String>(null)
            val collector = object : AnalyticsCollector {
                override fun recordEvent(name: String, attributes: Map<String, String>) = Unit
                override fun recordFailure(message: String, stackTrace: String?) = recordedStackTrace.set(stackTrace)
                override fun state(): Try<AnalyticsEntry> = Try.Failure(RuntimeException("Test failure"))
                override fun send() = Unit
                override val persistence: AnalyticsPersistence? = null
            }

            collector.recordFailure(RuntimeException("Recorded failure"))

            val trace = when (val value = recordedStackTrace.get()) {
                null -> fail("Expected a stack trace but none was found")
                else -> value
            }

            trace shouldInclude ("java.lang.RuntimeException: Recorded failure")
            trace shouldInclude ("at stasis.test.client_android.lib.telemetry.analytics.AnalyticsCollectorSpec")
            trace shouldInclude ("at io.kotest.engine.test.TestCaseExecutor")
        }
    }

    "A NoOp AnalyticsCollector" should {
        "record nothing" {
            val collector = AnalyticsCollector.NoOp

            collector.recordEvent("test_event")
            collector.recordEvent("test_event", result = Try.Success(Unit))
            collector.recordEvent("test_event", "a" to "b")
            collector.recordEvent("test_event", "a" to "b", "c" to "d")
            collector.recordEvent("test_event", mapOf("a" to "b"))

            collector.recordFailure(e = RuntimeException("Test failure"))
            collector.recordFailure(message = "Other failure", stackTrace = null)

            collector.send()

            collector.persistence shouldBe (null)

            val state = collector.state().get()
            state.events.isEmpty() shouldBe (true)
            state.failures.isEmpty() shouldBe (true)
        }
    }
})
