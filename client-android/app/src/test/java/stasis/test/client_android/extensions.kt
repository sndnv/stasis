package stasis.test.client_android

import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

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
