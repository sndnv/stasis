package stasis.client_android.tracking

import android.content.Context
import android.os.HandlerThread
import android.os.Process
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.Fixtures
import stasis.client_android.await
import stasis.client_android.eventually
import stasis.client_android.lib.model.SourceEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.lib.utils.Either
import stasis.client_android.notNull
import java.nio.file.Paths
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultBackupTrackerSpec {
    @Test
    fun trackBackupEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultBackupTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()
        val file1 = Paths.get("/tmp/test-1").toAbsolutePath()
        val file2 = Paths.get("/tmp/test-2").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        val sourceEntity = SourceEntity(
            path = file1,
            existingMetadata = null,
            currentMetadata = Fixtures.Metadata.FileOneMetadata
        )

        tracker.started(operation, UUID.randomUUID())
        tracker.entityDiscovered(operation, entity = file1)
        tracker.entityDiscovered(operation, entity = file2)
        tracker.specificationProcessed(operation, unmatched = emptyList())
        tracker.entityExamined(operation, entity = file1)
        tracker.entityExamined(operation, entity = file2)
        tracker.entitySkipped(operation, entity = file2)
        tracker.entityCollected(operation, entity = sourceEntity)
        tracker.entityProcessingStarted(operation, entity = file2, expectedParts = 3)
        tracker.entityPartProcessed(operation, entity = file2)
        tracker.entityPartProcessed(operation, entity = file2)
        tracker.entityProcessed(operation, entity = file1, metadata = Either.Left(Fixtures.Metadata.FileOneMetadata))
        tracker.metadataCollected(operation)
        tracker.metadataPushed(operation, entry = UUID.randomUUID())
        tracker.failureEncountered(operation, entity = file1, failure = RuntimeException("Test failure 1"))
        tracker.failureEncountered(operation, failure = RuntimeException("Test failure 2"))
        tracker.completed(operation)

        runBlocking {
            eventually {
                val completedState = tracker.state.await()[operation].notNull()

                assertThat(completedState.completed, not(equalTo(null)))
                assertThat(completedState.entities.discovered.size, equalTo(2))
                assertThat(completedState.entities.unmatched.size, equalTo(0))
                assertThat(completedState.entities.examined.size, equalTo(2))
                assertThat(completedState.entities.skipped.size, equalTo(1))
                assertThat(completedState.entities.collected.size, equalTo(1))
                assertThat(completedState.entities.pending.size, equalTo(1))
                assertThat(completedState.entities.processed.size, equalTo(1))
                assertThat(completedState.entities.failed.size, equalTo(1))
                assertThat(completedState.metadataCollected, not(equalTo(null)))
                assertThat(completedState.metadataPushed, not(equalTo(null)))

                assertThat(completedState.failures.size, equalTo(1))
            }
        }
    }

    @Test
    fun trackBackupEventsWithUnmatchedRules() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultBackupTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        val rule1 = stasis.client_android.lib.collection.rules.Rule(
            id = 0,
            operation = stasis.client_android.lib.collection.rules.Rule.Operation.Include,
            directory = "/tmp/1",
            pattern = "*"
        )

        val rule2 = rule1.copy(directory = "/tmp/2")

        val rule3 = rule1.copy(directory = "/tmp/3")

        tracker.started(operation, definition = UUID.randomUUID())
        tracker.specificationProcessed(
            operation = operation,
            unmatched = listOf(
                rule1 to RuntimeException("Test failure 1"),
                rule2 to RuntimeException("Test failure 2"),
                rule3 to RuntimeException("Test failure 3"),
            )
        )

        runBlocking {
            eventually {
                val completedState = tracker.state.await()[operation].notNull()

                assertThat(
                    completedState.entities.unmatched.sorted(),
                    equalTo(
                        listOf(
                            "Rule [+ /tmp/1 *] failed with [Test failure 1]",
                            "Rule [+ /tmp/2 *] failed with [Test failure 2]",
                            "Rule [+ /tmp/3 *] failed with [Test failure 3]",
                        )
                    )
                )
            }
        }
    }

    @Test
    fun provideBackupUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultBackupTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        runBlocking {
            tracker.started(operation, definition = UUID.randomUUID())
            tracker.entityExamined(operation, entity = file)
            tracker.entitySkipped(operation, entity = file)

            eventually {
                val operationStateExamined = tracker.updates(operation).await()
                assertThat(operationStateExamined.entities.examined.size, equalTo(1))
                assertThat(operationStateExamined.entities.skipped.size, equalTo(1))
                assertThat(operationStateExamined.completed, equalTo(null))
            }

            val otherOperation = Operation.generateId()
            tracker.started(otherOperation, definition = UUID.randomUUID())
            tracker.entityDiscovered(operation = otherOperation, entity = file) // other operation
            tracker.completed(operation)

            eventually {
                val operationStateCompleted = tracker.updates(operation).await()
                assertThat(operationStateCompleted.entities.examined.size, equalTo(1))
                assertThat(operationStateCompleted.entities.skipped.size, equalTo(1))
                assertThat(operationStateCompleted.completed, not(equalTo(null)))
            }
        }
    }

    @Test
    fun provideBackupState() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultBackupTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        runBlocking {
            tracker.started(operation, definition = UUID.randomUUID())
            tracker.entityExamined(operation, entity = file)

            eventually {
                val state = tracker.stateOf(operation)
                assertThat(state?.entities?.examined?.size, equalTo(1))
                assertThat(state?.completed, equalTo(null))
            }
        }
    }

    @Test
    fun supportRemovingOperations() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultBackupTracker(context, trackerHandler.looper)

        val operation1: OperationId = UUID.randomUUID()
        val operation2: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        runBlocking {
            tracker.started(operation1, definition = UUID.randomUUID())
            tracker.entityExamined(operation1, entity = file)

            tracker.started(operation2, definition = UUID.randomUUID())

            eventually {
                val state = tracker.state.value ?: emptyMap()
                assertThat(state.containsKey(operation1), equalTo(true))
                assertThat(state.containsKey(operation2), equalTo(true))
            }

            tracker.remove(operation1)

            eventually {
                val state = tracker.state.value ?: emptyMap()
                assertThat(state.containsKey(operation1), equalTo(false))
                assertThat(state.containsKey(operation2), equalTo(true))
            }
        }
    }

    @Test
    fun supportClearingOperations() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultBackupTracker(context, trackerHandler.looper)

        val operation1: OperationId = UUID.randomUUID()
        val operation2: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        runBlocking {
            tracker.started(operation1, definition = UUID.randomUUID())
            tracker.entityExamined(operation1, entity = file)

            tracker.started(operation2, definition = UUID.randomUUID())

            eventually {
                val state = tracker.state.value ?: emptyMap()
                assertThat(state.containsKey(operation1), equalTo(true))
                assertThat(state.containsKey(operation2), equalTo(true))
            }

            tracker.clear()

            eventually {
                assertThat(tracker.state.value, equalTo(emptyMap()))
            }
        }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
}
