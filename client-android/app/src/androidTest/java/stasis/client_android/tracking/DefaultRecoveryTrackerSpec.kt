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
import stasis.client_android.lib.model.TargetEntity
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.OperationId
import stasis.client_android.notNull
import java.nio.file.Paths
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultRecoveryTrackerSpec {
    @Test
    fun trackRecoveryEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultRecoveryTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultRecoveryTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()
        val file1 = Paths.get("/tmp/test-1").toAbsolutePath()
        val file2 = Paths.get("/tmp/test-2").toAbsolutePath()

        val targetEntity = TargetEntity(
            path = file1,
            destination = TargetEntity.Destination.Default,
            existingMetadata = Fixtures.Metadata.FileOneMetadata,
            currentMetadata = null
        )

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        tracker.entityExamined(operation, entity = file1, metadataChanged = true, contentChanged = false)
        tracker.entityExamined(operation, entity = file2, metadataChanged = true, contentChanged = true)
        tracker.entityCollected(operation, entity = targetEntity)
        tracker.entityProcessingStarted(operation, entity = file1, expectedParts = 3)
        tracker.entityPartProcessed(operation, entity = file1)
        tracker.entityPartProcessed(operation, entity = file1)
        tracker.entityProcessed(operation, entity = file2)
        tracker.metadataApplied(operation, entity = file1)
        tracker.failureEncountered(operation, entity = file1, failure = RuntimeException("test failure 1"))
        tracker.failureEncountered(operation, failure = RuntimeException("test failure 2"))
        tracker.completed(operation)

        runBlocking {
            eventually {
                val completedState = tracker.state.await()[operation].notNull()

                assertThat(completedState.completed, not(equalTo(null)))

                assertThat(completedState.entities.examined.size, equalTo(2))
                assertThat(completedState.entities.collected.size, equalTo(1))
                assertThat(completedState.entities.pending.size, equalTo(1))
                assertThat(completedState.entities.processed.size, equalTo(1))
                assertThat(completedState.entities.metadataApplied.size, equalTo(1))
                assertThat(completedState.entities.failed.size, equalTo(1))

                assertThat(completedState.failures.size, equalTo(1))
            }
        }
    }

    @Test
    fun provideOperationUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultRecoveryTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultRecoveryTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        runBlocking {
            tracker.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = false)

            eventually {
                val operationStateExamined = tracker.updates(operation).await()
                assertThat(operationStateExamined.entities.examined.size, equalTo(1))
                assertThat(operationStateExamined.completed, equalTo(null))
            }

            tracker.metadataApplied(operation = Operation.generateId(), entity = file) // other operation
            tracker.completed(operation)

            eventually {
                val operationStateCompleted = tracker.updates(operation).await()
                assertThat(operationStateCompleted.entities.examined.size, equalTo(1))
                assertThat(operationStateCompleted.completed, not(equalTo(null)))
            }
        }
    }

    @Test
    fun provideRecoveryState() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val trackerHandler = HandlerThread(
            "DefaultRecoveryTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultRecoveryTracker(context, trackerHandler.looper)

        val operation: OperationId = UUID.randomUUID()
        val file = Paths.get("test").toAbsolutePath()

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))


        runBlocking {
            tracker.entityExamined(operation, entity = file, metadataChanged = true, contentChanged = false)

            eventually {
                val state = tracker.stateOf(operation)
                assertThat(state?.entities?.examined?.size, equalTo(1))
                assertThat(state?.completed, equalTo(null))
            }
        }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
}
