package stasis.client_android

import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.toKotlinDuration

@OptIn(ExperimentalTime::class)
suspend inline fun <reified T> eventually(
    duration: Duration = Duration.ofSeconds(3),
    interval: Duration = Duration.ofMillis(50),
    f: () -> T
): T {
    val start = TimeSource.Monotonic.markNow()
    val end = start.plus(duration.toKotlinDuration())

    var executions: Int = 0
    var failure: AssertionError? = null

    while (end.hasNotPassedNow()) {
        try {
            return f()
        } catch (e: Throwable) {
            when (e) {
                is AssertionError -> failure = e
                else -> throw e
            }
        }

        executions += 1
        delay(interval.toMillis())
    }

    val outcomeMessage =
        "Eventually failed after [${duration.toKotlinDuration()}] and [$executions] attempt(s), " +
                "with an interval of [${interval.toKotlinDuration()}]"

    val failureMessage = when (failure) {
        null -> "no failure"
        else -> "last failure was [${failure.javaClass.simpleName} - ${failure.message}]"
    }

    throw TestFailedException(message = "$outcomeMessage; $failureMessage", cause = failure)
}

class TestFailedException(message: String, cause: Throwable?) : Exception(message, cause)
