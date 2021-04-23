package stasis.client_android.lib.utils

object NonFatal {
    fun Throwable.isNonFatal(): Boolean = when (this) {
        is VirtualMachineError, is ThreadDeath, is InterruptedException, is LinkageError -> false
        else -> true
    }

    fun Throwable.nonFatal(): Throwable = if (isNonFatal()) {
        this
    } else {
        throw this
    }
}
