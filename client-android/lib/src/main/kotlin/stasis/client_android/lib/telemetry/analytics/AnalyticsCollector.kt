package stasis.client_android.lib.telemetry.analytics

import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.utils.Try

interface AnalyticsCollector {
    fun recordEvent(name: String): Unit =
        recordEvent(name = name, attributes = emptyMap())

    fun recordEvent(name: String, result: Try<*>): Unit =
        recordEvent(name = name, "result" to if (result.isSuccess) "success" else "failure")

    fun recordEvent(name: String, vararg attributes: Pair<String, String>): Unit =
        recordEvent(name = name, attributes = attributes.toMap())

    fun recordFailure(e: Throwable): Unit = recordFailure(message = "${e.javaClass.simpleName} - ${e.message}")

    fun recordEvent(name: String, attributes: Map<String, String>)

    fun recordFailure(message: String)

    fun state(): Try<AnalyticsEntry>

    fun send()

    val persistence: AnalyticsPersistence?

    object NoOp : AnalyticsCollector {
        override fun recordEvent(name: String, attributes: Map<String, String>) = Unit

        override fun recordFailure(message: String) = Unit

        override fun state(): Try<AnalyticsEntry> =
            Try.Success(AnalyticsEntry.collected(app = ApplicationInformation.none()))

        override fun send() = Unit

        override val persistence: AnalyticsPersistence? = null
    }
}
