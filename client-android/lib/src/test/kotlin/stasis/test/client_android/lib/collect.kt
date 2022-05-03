package stasis.test.client_android.lib

import kotlinx.coroutines.delay
import stasis.client_android.lib.utils.Try
import java.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.toKotlinDuration

@OptIn(ExperimentalTime::class)
suspend inline fun <reified T> collectPeriodically(
    duration: Duration = Duration.ofSeconds(1),
    interval: Duration = Duration.ofMillis(100),
    f: () -> T
): List<Try<T>> {
    val start = TimeSource.Monotonic.markNow()
    val end = start.plus(duration.toKotlinDuration())

    var executions: Int = 0
    val collected = mutableListOf<Try<T>>()

    while (end.hasNotPassedNow()) {
        collected += Try { f() }
        executions += 1
        delay(interval.toMillis())
    }

    return collected.toList()
}
