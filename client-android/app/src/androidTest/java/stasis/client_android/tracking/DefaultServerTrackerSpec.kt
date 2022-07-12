package stasis.client_android.tracking

import android.os.HandlerThread
import android.os.Process
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.await
import stasis.client_android.eventually

@RunWith(AndroidJUnit4::class)
class DefaultServerTrackerSpec {
    @Test
    fun trackServerEvents() {
        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultServerTracker(trackerHandler.looper)

        val server1 = "test-server-01"
        val server2 = "test-server-02"

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        tracker.reachable(server1)
        tracker.reachable(server2)
        tracker.unreachable(server1)

        runBlocking {
            eventually {
                val actual = tracker.state.await().mapValues { it.value.reachable }

                val expected = mapOf(
                    server1 to false,
                    server2 to true
                )

                assertThat(actual, equalTo(expected))
            }
        }
    }

    @Test
    fun providesUpdates() {
        val trackerHandler = HandlerThread(
            "DefaultBackupTrackerSpec",
            Process.THREAD_PRIORITY_BACKGROUND
        ).apply { start() }

        val tracker = DefaultServerTracker(trackerHandler.looper)

        val server1 = "test-server-01"
        val server2 = "test-server-02"

        val initialState = tracker.state.value

        assertThat(initialState, equalTo(emptyMap()))

        runBlocking {
            tracker.reachable(server1)

            eventually {
                val state = tracker.updates(server1).await()
                assertThat(state.reachable, equalTo(true))
            }

            tracker.reachable(server2) // other operation
            tracker.unreachable(server1)

            eventually {
                val stateServer1 = tracker.updates(server1).await()
                val stateServer2 = tracker.updates(server2).await()
                assertThat(stateServer1.reachable, equalTo(false))
                assertThat(stateServer2.reachable, equalTo(true))
            }
        }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
}
