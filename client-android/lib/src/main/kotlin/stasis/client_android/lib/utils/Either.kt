package stasis.client_android.lib.utils

sealed class Either<L, R> {
    abstract val left: L
    abstract val right: R
    abstract val leftOpt: L?
    abstract val rightOpt: R?
    abstract val isLeft: Boolean
    abstract val isRight: Boolean

    fun <R2> map(f: (R) -> R2): Either<L, R2> =
        when (this) {
            is Right -> Right(f(value))
            is Left -> Left(value)
        }

    fun <R2> flatMap(f: (R) -> Either<L, R2>): Either<L, R2> =
        when (this) {
            is Right -> f(value)
            is Left -> Left(value)
        }

    fun <T> fold(fl: (L) -> T, fr: (R) -> T): T =
        when (this) {
            is Right -> fr(value)
            is Left -> fl(value)
        }

    data class Left<L, R>(val value: L) : Either<L, R>() {
        override val left: L = value
        override val right: R get() = throw NoSuchElementException("Cannot get 'right' on Either.left")
        override val isLeft: Boolean = true
        override val isRight: Boolean = false
        override val leftOpt: L? = value
        override val rightOpt: R? = null
    }

    data class Right<L, R>(val value: R) : Either<L, R>() {
        override val left: L get() = throw NoSuchElementException("Cannot get 'left' on Either.right")
        override val right: R = value
        override val isLeft: Boolean = false
        override val isRight: Boolean = true
        override val leftOpt: L? = null
        override val rightOpt: R? = value
    }
}
