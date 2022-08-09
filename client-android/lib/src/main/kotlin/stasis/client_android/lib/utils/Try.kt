package stasis.client_android.lib.utils

import stasis.client_android.lib.utils.NonFatal.nonFatal

sealed class Try<T> {
    abstract fun get(): T
    abstract fun getOrElse(default: () -> T): T
    abstract fun toOption(): T?
    abstract fun failed(): Try<Throwable>

    abstract val isSuccess: Boolean
    abstract val isFailure: Boolean

    data class Success<T>(val value: T) : Try<T>() {
        override fun get(): T = value
        override fun getOrElse(default: () -> T): T = value
        override fun toOption(): T? = value
        override fun failed(): Try<Throwable> = Failure(UnsupportedOperationException("Success.failed"))

        override val isSuccess: Boolean = true
        override val isFailure: Boolean = false
    }

    data class Failure<T>(val exception: Throwable) : Try<T>() {
        override fun get(): T = throw exception
        override fun getOrElse(default: () -> T): T = default()
        override fun toOption(): T? = null
        override fun failed(): Try<Throwable> = Success(exception)

        override val isSuccess: Boolean = false
        override val isFailure: Boolean = true
    }

    companion object {
        inline operator fun <T> invoke(f: () -> T): Try<T> =
            try {
                Success(f())
            } catch (e: Throwable) {
                Failure(e.nonFatal())
            }

        operator fun <T> invoke(seq: List<Try<T>>): Try<List<T>> =
            Try { seq.map { it.get() } }

        inline fun <T, T2> Try<T>.map(f: (T) -> T2): Try<T2> =
            when (this) {
                is Failure -> Failure(exception)
                is Success -> Try { f(value) }
            }

        inline fun <T, T2> Try<T>.flatMap(f: (T) -> Try<T2>): Try<T2> =
            when (this) {
                is Failure -> Failure(exception)
                is Success -> Try { f(value) }.flatten()
            }

        inline fun <T> Try<T>.foreach(f: (T) -> Unit) =
            when (this) {
                is Failure -> Unit
                is Success -> f(value)
            }

        inline fun <T> Try<T>.recover(f: (Throwable) -> T): Try<T> =
            when (this) {
                is Failure -> Try { f(exception) }
                is Success -> this
            }

        inline fun <T> Try<T>.recoverWith(f: (Throwable) -> Try<T>): Try<T> =
            when (this) {
                is Failure -> Try { f(exception) }.flatten()
                is Success -> this
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
