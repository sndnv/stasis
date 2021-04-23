package stasis.client_android.lib.ops

import java.time.Instant
import java.util.UUID

interface Operation {
    val id: OperationId
    suspend fun start()
    fun stop()
    fun type(): Type

    companion object {
        fun generateId(): OperationId = UUID.randomUUID()
    }

    sealed class Type {
        object Backup : Type()
        object Recovery : Type()
        object Expiration : Type()
        object Validation : Type()
        object KeyRotation : Type()
        object GarbageCollection : Type()
    }

    data class Progress(
        val stages: Map<String, Stage>,
        val failures: List<String>,
        val completed: Instant?
    ) {
        fun empty(): Progress =
            Progress(
                failures = emptyList(),
                stages = emptyMap(),
                completed = null
            )

        data class Stage(val steps: List<Step>) {
            fun withStep(step: Step): Stage = copy(steps = steps + step)

            data class Step(val name: String, val completed: Instant)
        }
    }
}

typealias OperationId = UUID
