package stasis.client_android.tracking

import androidx.lifecycle.LiveData
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import java.time.Instant

interface TrackerView {
    val state: LiveData<State>

    fun operationUpdates(operation: OperationId): LiveData<Operation.Progress>

    data class State(
        val operations: Map<OperationId, Operation.Progress>,
        val servers: Map<String, ServerState>
    ) {
        fun withServer(server: String, reachable: Boolean): State {
            return copy(servers = servers + (server to ServerState(reachable = reachable, Instant.now())))
        }

        fun withStep(operationId: OperationId, stage: String, step: String): State {
            val existingProgress = operations.getOrElse(operationId) { Operation.Progress.empty() }
            val existingStage = existingProgress.stages.getOrElse(stage) { Operation.Progress.Stage(steps = emptyList()) }

            val updatedStage = existingStage.withStep(Operation.Progress.Stage.Step(name = step, completed = Instant.now()))
            val updatedProgress = existingProgress.copy(stages = existingProgress.stages + (stage to updatedStage))

            return copy(operations = operations + (operationId to updatedProgress))
        }

        fun withFailure(operationId: OperationId, failure: Throwable): State {
            val existingProgress = operations.getOrElse(operationId) { Operation.Progress.empty() }
            val updatedProgress = existingProgress.copy(
                failures = existingProgress.failures + "${failure.javaClass.simpleName}: ${failure.message}"
            )

            return copy(operations = operations + (operationId to updatedProgress))
        }

        fun completed(operationId: OperationId): State {
            val existingProgress = operations.getOrElse(operationId) { Operation.Progress.empty() }
            val updatedProgress = existingProgress.copy(completed = Instant.now())

            return copy(operations = operations + (operationId to updatedProgress))
        }

        companion object {
            fun empty(): State = State(
                operations = emptyMap(),
                servers = emptyMap()
            )
        }
    }

    data class ServerState(
        val reachable: Boolean,
        val timestamp: Instant
    )
}
