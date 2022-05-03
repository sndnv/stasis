package stasis.test.client_android.lib

import kotlinx.coroutines.delay
import java.time.Duration

suspend inline fun <reified T> awaitAndThen(
    duration: Duration = Duration.ofSeconds(3),
    f: () -> T
): T {
    delay(duration.toMillis())
    return f()
}
