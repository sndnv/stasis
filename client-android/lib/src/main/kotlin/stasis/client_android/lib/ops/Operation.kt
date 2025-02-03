package stasis.client_android.lib.ops

import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.util.UUID

interface Operation {
    val id: OperationId
    fun start(withScope: CoroutineScope, f: (Throwable?) -> Unit)
    fun stop()
    fun type(): Type

    companion object {
        fun generateId(): OperationId = UUID.randomUUID()
    }

    sealed class Type {
        override fun toString(): String = javaClass.simpleName

        object Backup : Type()
        object Recovery : Type()
        object Expiration : Type()
        object Validation : Type()
        object KeyRotation : Type()
        object GarbageCollection : Type()

        companion object {
            fun fromString(string: String): Type =
                when (string) {
                    "Backup" -> Backup
                    "Recovery" -> Recovery
                    "Expiration" -> Expiration
                    "Validation" -> Validation
                    "KeyRotation" -> KeyRotation
                    "GarbageCollection" -> GarbageCollection
                    else -> throw IllegalArgumentException("Unexpected operation type provided: [$string]")
                }
        }
    }

    data class Progress(
        val started: Instant,
        val total: Int,
        val processed: Int,
        val failures: Int,
        val completed: Instant?
    )

    sealed class Restriction {
        data object NoConnection : Restriction()
        data object LimitedNetwork : Restriction()
    }
}

typealias OperationId = UUID
