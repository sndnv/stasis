package stasis.client_android.tracking

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.await
import stasis.client_android.eventually
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.notNull
import java.nio.file.Paths
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultTrackerSpec {
    @Test
    fun trackBackupEvents() {
        val tracker = DefaultTracker()

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(TrackerView.State.empty()))

        tracker.backup.entityDiscovered(operation, entity = file)
        tracker.backup.entityDiscovered(operation, entity = file)
        tracker.backup.entityDiscovered(operation, entity = file)
        tracker.backup.entityDiscovered(operation, entity = file)
        tracker.backup.specificationProcessed(operation, unmatched = emptyList())
        tracker.backup.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = false)
        tracker.backup.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = true)
        tracker.backup.entityCollected(operation, entity = file)
        tracker.backup.entityProcessed(operation, entity = file, contentChanged = true)
        tracker.backup.metadataCollected(operation)
        tracker.backup.metadataPushed(operation, entry = UUID.randomUUID())
        tracker.backup.failureEncountered(operation, failure = RuntimeException("test failure"))
        tracker.backup.completed(operation)

        runBlocking {
            eventually {
                val completedState = tracker.state.await().operations[operation].notNull()

                assertThat(completedState.completed != null, equalTo(true))

                assertThat(completedState.stages.contains("discovery"), equalTo(true))
                assertThat(completedState.stages["discovery"]!!.steps.size, equalTo(4))

                assertThat(completedState.stages.contains("specification"), equalTo(true))
                assertThat(completedState.stages["specification"]!!.steps.size, equalTo(1))

                assertThat(completedState.stages.contains("examination"), equalTo(true))
                assertThat(completedState.stages["examination"]!!.steps.size, equalTo(2))

                assertThat(completedState.stages.contains("collection"), equalTo(true))
                assertThat(completedState.stages["collection"]!!.steps.size, equalTo(1))

                assertThat(completedState.stages.contains("processing"), equalTo(true))
                assertThat(completedState.stages["processing"]!!.steps.size, equalTo(1))

                assertThat(completedState.stages.contains("metadata"), equalTo(true))
                assertThat(completedState.stages["metadata"]!!.steps.size, equalTo(2))

                assertThat(completedState.failures, equalTo(listOf("test failure")))
            }
        }
    }

    @Test
    fun trackBackupEventsWithUnmatchedRules() {
        val tracker = DefaultTracker()

        val operation: OperationId = UUID.randomUUID()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(TrackerView.State.empty()))

        val rule1 = stasis.client_android.lib.collection.rules.Rule(
            id = 0,
            operation = stasis.client_android.lib.collection.rules.Rule.Operation.Include,
            directory = "/tmp/1",
            pattern = "*"
        )

        val rule2 = rule1.copy(directory = "/tmp/2")

        val rule3 = rule1.copy(directory = "/tmp/3")

        tracker.backup.specificationProcessed(
            operation = operation,
            unmatched = listOf(
                rule1 to RuntimeException("Test failure"),
                rule2 to RuntimeException("Test failure"),
                rule3 to RuntimeException("Test failure"),
            )
        )

        runBlocking {
            eventually {
                val completedState = tracker.state.await().operations[operation].notNull()

                assertThat(
                    completedState.failures.sorted(),
                    equalTo(
                        listOf(
                            "Rule [+ /tmp/1 *] failed with [Test failure]",
                            "Rule [+ /tmp/2 *] failed with [Test failure]",
                            "Rule [+ /tmp/3 *] failed with [Test failure]",
                        )
                    )
                )
            }
        }
    }

    @Test
    fun trackRecoveryEvents() {
        val tracker = DefaultTracker()

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(TrackerView.State.empty()))

        tracker.recovery.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = false)
        tracker.recovery.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = true)
        tracker.recovery.entityCollected(operation, entity = file)
        tracker.recovery.entityProcessed(operation, entity = file)
        tracker.recovery.metadataApplied(operation, entity = file)
        tracker.recovery.failureEncountered(operation, failure = RuntimeException("test failure"))
        tracker.recovery.completed(operation)

        runBlocking {
            eventually {
                val completedState = tracker.state.await().operations[operation].notNull()

                assertThat(completedState.completed != null, equalTo(true))

                assertThat(completedState.stages.contains("examination"), equalTo(true))
                assertThat(completedState.stages["examination"]!!.steps.size, equalTo(2))

                assertThat(completedState.stages.contains("collection"), equalTo(true))
                assertThat(completedState.stages["collection"]!!.steps.size, equalTo(1))

                assertThat(completedState.stages.contains("processing"), equalTo(true))
                assertThat(completedState.stages["processing"]!!.steps.size, equalTo(1))

                assertThat(completedState.stages.contains("metadata-applied"), equalTo(true))
                assertThat(completedState.stages["metadata-applied"]!!.steps.size, equalTo(1))

                assertThat(completedState.failures, equalTo(listOf("test failure")))
            }
        }
    }

    @Test
    fun trackServerEvents() {
        val tracker = DefaultTracker()

        val server1 = "test-server-01"
        val server2 = "test-server-02"

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(TrackerView.State.empty()))

        tracker.server.reachable(server1)
        tracker.server.reachable(server2)
        tracker.server.unreachable(server1)

        runBlocking {
            eventually {
                val actual = tracker.state.await().servers.mapValues { it.value.reachable }

                val expected = mapOf(
                    server1 to false,
                    server2 to true
                )

                assertThat(actual, equalTo(expected))
            }
        }
    }

    @Test
    fun provideOperationUpdates() {
        val tracker = DefaultTracker()

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(TrackerView.State.empty()))

        runBlocking {
            tracker.backup.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = false)

            eventually {
                val operationStateExamined = tracker.operationUpdates(operation).await()
                assertThat(operationStateExamined.stages.size, equalTo(1))
                assertThat(operationStateExamined.completed, equalTo(null))
            }

            tracker.backup.entityCollected(operation = Operation.generateId(), entity = file) // other operation
            tracker.recovery.completed(operation)

            eventually {
                val operationStateCompleted = tracker.operationUpdates(operation).await()
                assertThat(operationStateCompleted.stages.size, equalTo(1))
                assertThat(operationStateCompleted.completed, not(equalTo(null)))
            }
        }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
}
