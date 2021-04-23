package stasis.client_android.lib.utils

import stasis.client_android.lib.utils.NonFatal.nonFatal

sealed class Try<T> {
    abstract fun get(): T
    abstract fun <T2> map(f: (T) -> T2): Try<T2>
    abstract fun <T2> flatMap(f: (T) -> Try<T2>): Try<T2>

    abstract val isSuccess: Boolean
    abstract val isFailure: Boolean

    data class Success<T>(val value: T) : Try<T>() {
        override fun get(): T = value
        override fun <T2> map(f: (T) -> T2): Try<T2> = Try { f(value) }
        override fun <T2> flatMap(f: (T) -> Try<T2>): Try<T2> = Try { f(value) }.flatten()

        override val isSuccess: Boolean = true
        override val isFailure: Boolean = false
    }

    data class Failure<T>(val exception: Throwable) : Try<T>() {
        override fun get(): T = throw exception
        override fun <T2> map(f: (T) -> T2): Try<T2> = Failure(exception)
        override fun <T2> flatMap(f: (T) -> Try<T2>): Try<T2> = Failure(exception)

        override val isSuccess: Boolean = false
        override val isFailure: Boolean = true
    }

    companion object {
        operator fun <T> invoke(f: () -> T): Try<T> =
            try {
                Success(f())
            } catch (e: Throwable) {
                Failure(e.nonFatal())
            }

        fun <T> Try<Try<T>>.flatten(): Try<T> =
            when (this) {
                is Failure -> Failure(exception)
                is Success -> value
            }

        fun <T> Try<T>.toEither(): Either<Throwable, T> =
            when (this) {
                is Failure -> Either.Left(exception)
                is Success -> Either.Right(value)
            }

        fun <T> Either<Throwable, T>.toTry(): Try<T> =
            when (this) {
                is Either.Left -> Failure(value)
                is Either.Right -> Success(value)
            }
    }
}
