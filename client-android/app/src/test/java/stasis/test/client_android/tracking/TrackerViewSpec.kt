package stasis.test.client_android.tracking

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.ops.Operation
import stasis.client_android.tracking.TrackerView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class TrackerViewSpec {
    @Test
    fun supportUpdatingServerState() {
        val state = TrackerView.State.empty()
        val server = "test-server"

        val actualState = state.withServer(server = server, reachable = true)

        val expectedState = TrackerView.State(
            operations = emptyMap(),
            servers = mapOf(
                server to TrackerView.ServerState(
                    reachable = true,
                    timestamp = actualState.servers[server]!!.timestamp
                )
            )
        )

        assertThat(actualState, equalTo(expectedState))
    }

    @Test
    fun supportUpdatingOperationState() {
        val state = TrackerView.State.empty()
        val operation = Operation.generateId()

        val actualState = state.withStep(
            operationId = operation,
            stage = "test-stage",
            step = "test-step"
        )

        val expectedState = TrackerView.State(
            operations = mapOf(
                operation to Operation.Progress(
                    stages = mapOf(
                        "test-stage" to Operation.Progress.Stage(
                            steps = listOf(
                                Operation.Progress.Stage.Step(
                                    name = "test-step",
                                    completed = actualState.operations[operation]!!.stages["test-stage"]!!.steps.first().completed
                                )
                            )
                        )
                    ),
                    failures = emptyList(),
                    completed = null
                )
            ),
            servers = emptyMap()
        )

        assertThat(actualState, equalTo(expectedState))
    }

    @Test
    fun supportUpdatingStepStateFailed() {
        val state = TrackerView.State.empty()
        val operation = Operation.generateId()

        val actualState = state.withFailure(
            operationId = operation,
            failure = RuntimeException("test failure")
        )

        val expectedState = TrackerView.State(
            operations = mapOf(
                operation to Operation.Progress(
                    stages = emptyMap(),
                    failures = listOf("RuntimeException: test failure"),
                    completed = null
                )
            ),
            servers = emptyMap()
        )

        assertThat(actualState, equalTo(expectedState))
    }

    @Test
    fun supportUpdatingStepStateCompleted() {
        val state = TrackerView.State.empty()
        val operation = Operation.generateId()

        val actualState = state.completed(operationId = operation)

        val expectedState = TrackerView.State(
            operations = mapOf(
                operation to Operation.Progress(
                    stages = emptyMap(),
                    failures = emptyList(),
                    completed = actualState.operations[operation]!!.completed
                )
            ),
            servers = emptyMap()
        )

        assertThat(actualState, equalTo(expectedState))
    }
}
