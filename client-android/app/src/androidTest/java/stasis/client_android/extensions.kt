package stasis.client_android

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

fun <T> LiveData<T>.await(): T = await(duration = Duration.ofSeconds(3))

fun <T> LiveData<T>.await(duration: Duration): T {
    var result: T? = null

    val count = CountDownLatch(1)

    (object : Observer<T> {
        override fun onChanged(value: T) {
            result = value
            count.countDown()
            this@await.removeObserver(this)
        }
    }).apply(::observeForever)

    count.await(duration.toMillis(), TimeUnit.MILLISECONDS)

    return result ?: throw TimeoutException("Failed to retrieve data")
}

inline fun <reified T> T?.notNull(): T =
    this ?: throw AssertionError("Expected value but null found")

inline fun <reified T> Try<T>.failure(): Throwable =
    when (this) {
        is Success -> throw AssertionError("Expected failure but a successful result was found: [${this.value}]")
        is Failure -> this.exception
    }

inline fun <reified T> Try<T>.success(): T =
    when (this) {
        is Success -> this.value
        is Failure -> throw AssertionError("Expected successful result but failure found: [${this.exception}]")
    }
