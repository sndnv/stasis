package stasis.client_android.telemetry.analytics

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.eventually
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.mocks.MockAnalyticsPersistence
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DefaultAnalyticsCollectorSpec {
    @Test
    fun recordEvents() {
        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = MockAnalyticsPersistence(existing = Try.Success(null))
        )

        collector.recordEvent("test_event")
        collector.recordEvent("test_event", "a" to "b")
        collector.recordEvent("test_event", "a" to "b", "c" to "d")
        collector.recordEvent("test_event", mapOf("a" to "b"))

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(4))

                assertThat(state.events[0].id, equalTo(0))
                assertThat(state.events[0].event, equalTo("test_event"))

                assertThat(state.events[1].id, equalTo(1))
                assertThat(state.events[1].event, equalTo("test_event{a='b'}"))

                assertThat(state.events[2].id, equalTo(2))
                assertThat(state.events[2].event, equalTo("test_event{a='b',c='d'}"))

                assertThat(state.events[3].id, equalTo(3))
                assertThat(state.events[3].event, equalTo("test_event{a='b'}"))
            }
        }
    }

    @Test
    fun recordFailures() {
        val persistence = object : MockAnalyticsPersistence(existing = Try.Success(null)) {
            override val lastTransmitted: Instant
                get() = Instant.now() // prevents transmission
        }

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence
        )

        collector.recordFailure(RuntimeException("Test failure"))
        collector.recordFailure("Other failure")

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.failures.size, equalTo(2))

                assertThat(state.failures[0].message, equalTo("RuntimeException - Test failure"))
                assertThat(state.failures[1].message, equalTo("Other failure"))
            }
        }
    }


    @Test
    fun supportLoadingCacheState() {
        val persistence = MockAnalyticsPersistence(
            existing = Try.Success(
                AnalyticsEntry
                    .collected(app = ApplicationInformation.none())
                    .withEvent(name = "existing_event", attributes = emptyMap())
                    .withFailure(message = "Existing failure")
            )
        )

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence,
        )

        collector.recordEvent("test_event")

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(2))

                assertThat(state.events[0].id, equalTo(0))
                assertThat(state.events[0].event, equalTo("existing_event"))

                assertThat(state.events[1].id, equalTo(1))
                assertThat(state.events[1].event, equalTo("test_event"))

                assertThat(persistence.cached.size, equalTo(0))
                assertThat(persistence.transmitted.size, equalTo(0))
            }
        }
    }

    @Test
    fun handleFailuresWhenLoadingCachedState() {
        val persistence = MockAnalyticsPersistence(existing = Try.Failure(RuntimeException("Test failure")))

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence,
        )

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(0))
                assertThat(state.failures.size, equalTo(0))

                assertThat(persistence.cached.size, equalTo(0))
                assertThat(persistence.transmitted.size, equalTo(0))
            }
        }
    }

    @Test
    fun supportCachingStateLocally() {
        val persistence = object : MockAnalyticsPersistence(existing = Try.Success(null)) {
            override val lastTransmitted: Instant
                get() = Instant.now() // prevents transmission
        }

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofMillis(100),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence,
        )

        collector.recordEvent("test_event")
        runBlocking { delay(timeMillis = 75) }
        collector.recordEvent("test_event", "a" to "b")
        runBlocking { delay(timeMillis = 75) }
        collector.recordEvent("test_event", "a" to "b", "c" to "d")
        runBlocking { delay(timeMillis = 75) }
        collector.recordEvent("test_event", mapOf("a" to "b"))
        runBlocking { delay(timeMillis = 150) }
        collector.recordFailure("Test failure")

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(4))
                assertThat(state.failures.size, equalTo(1))

                assertThat(persistence.cached.size, equalTo(3))

                assertThat(persistence.cached[0].events.size, equalTo(2))
                assertThat(persistence.cached[0].failures.size, equalTo(0))

                assertThat(persistence.cached[1].events.size, equalTo(4))
                assertThat(persistence.cached[1].failures.size, equalTo(0))

                assertThat(persistence.cached[2].events.size, equalTo(4))
                assertThat(persistence.cached[2].failures.size, equalTo(1))

                assertThat(persistence.transmitted.size, equalTo(0))
            }
        }
    }

    @Test
    fun supportTransmittingStateRemotely() {
        val persistence = MockAnalyticsPersistence(existing = Try.Success(null))

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence,
        )

        collector.recordEvent("test_event")
        collector.recordFailure("Test failure")

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(0))
                assertThat(state.failures.size, equalTo(0))

                assertThat(persistence.cached.size, equalTo(1))

                assertThat(persistence.cached[0].events.size, equalTo(0))
                assertThat(persistence.cached[0].failures.size, equalTo(0))

                assertThat(persistence.transmitted.size, equalTo(1))

                assertThat(persistence.transmitted[0].events.size, equalTo(1))
                assertThat(persistence.transmitted[0].failures.size, equalTo(1))
            }
        }
    }

    @Test
    fun handleTransmissionFailures() {
        val persistence = object : MockAnalyticsPersistence(existing = Try.Success(null)) {
            override suspend fun transmit(entry: AnalyticsEntry): Try<Unit> =
                Try.Failure(RuntimeException("Test failure"))
        }

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence,
        )

        collector.recordEvent("test_event")
        collector.recordFailure(message = "Test failure")

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(1))
                assertThat(state.failures.size, equalTo(1))

                assertThat(persistence.cached.size, equalTo(1))

                assertThat(persistence.cached[0].events.size, equalTo(1))
                assertThat(persistence.cached[0].failures.size, equalTo(1))

                assertThat(persistence.transmitted.size, equalTo(0))
            }
        }
    }

    @Test
    fun supportTransmittingStateRemotelyOnDemand() {
        val persistence = object : MockAnalyticsPersistence(existing = Try.Success(null)) {
            override val lastTransmitted: Instant
                get() = Instant.now() // prevents transmission
        }

        val collector = DefaultAnalyticsCollector(
            app = ApplicationInformation.none(),
            persistenceInterval = Duration.ofSeconds(60),
            transmissionInterval = Duration.ofSeconds(60),
            persistence = persistence,
        )

        collector.recordFailure("Test failure")

        runBlocking {
            eventually {
                val state = collector.state().get()

                assertThat(state.events.size, equalTo(0))
                assertThat(state.failures.size, equalTo(1))

                assertThat(persistence.cached.size, equalTo(1))

                assertThat(persistence.cached[0].events.size, equalTo(0))
                assertThat(persistence.cached[0].failures.size, equalTo(1))
            }
        }

        assertThat(persistence.transmitted.size, equalTo(0))

        collector.send()

        runBlocking {
            eventually {
                assertThat(persistence.transmitted.size, equalTo(1))

                assertThat(persistence.transmitted[0].events.size, equalTo(0))
                assertThat(persistence.transmitted[0].failures.size, equalTo(1))
            }
        }
    }
}
