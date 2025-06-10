package stasis.test.client_android.lib.ops.monitoring

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.client_android.lib.ops.monitoring.DefaultServerMonitor
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.mocks.MockServerTracker
import java.time.Duration
import java.util.UUID

class DefaultServerMonitorSpec : WordSpec({
    "A DefaultServerMonitor" should {
        val defaultInterval: Duration = Duration.ofMillis(100)

        suspend fun <T> managedMonitor(monitor: DefaultServerMonitor, block: suspend () -> T): T =
            try {
                block()
            } finally {
                monitor.stop()
            }

        "support pinging servers periodically" {
            val mockApiClient = MockServerApiEndpointClient()

            val mockTracker = MockServerTracker()

            val monitor = DefaultServerMonitor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval,
                api = mockApiClient,
                tracker = mockTracker,
                scope = testScope
            )

            managedMonitor(monitor) {
                delay(defaultInterval.toMillis() / 2)

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (1)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

                mockTracker.statistics[MockServerTracker.Statistic.ServerReachable] shouldBe (1)
                mockTracker.statistics[MockServerTracker.Statistic.ServerUnreachable] shouldBe (0)

                delay(defaultInterval.toMillis())

                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (2)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

                mockTracker.statistics[MockServerTracker.Statistic.ServerReachable] shouldBe (2)
                mockTracker.statistics[MockServerTracker.Statistic.ServerUnreachable] shouldBe (0)
            }
        }

        "handle ping failures" {
            val mockApiClient = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun ping(): Try<Ping> =
                    Try.Failure(RuntimeException("test failure"))
            }

            val mockTracker = MockServerTracker()

            val monitor = DefaultServerMonitor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval.dividedBy(2),
                api = mockApiClient,
                tracker = mockTracker,
                scope = testScope
            )

            managedMonitor(monitor) {
                delay(defaultInterval.toMillis())

                mockTracker.statistics[MockServerTracker.Statistic.ServerReachable] shouldBe (0)
                mockTracker.statistics[MockServerTracker.Statistic.ServerUnreachable]!! shouldBeGreaterThanOrEqual (3)
            }
        }

        "support stopping itself" {
            val mockTracker = MockServerTracker()

            val monitor = DefaultServerMonitor(
                initialDelay = Duration.ZERO,
                interval = defaultInterval.dividedBy(2),
                api = MockServerApiEndpointClient(),
                tracker = mockTracker,
                scope = testScope
            )

            delay(defaultInterval.toMillis())

            mockTracker.statistics[MockServerTracker.Statistic.ServerReachable]!! shouldBeGreaterThanOrEqual (2)
            mockTracker.statistics[MockServerTracker.Statistic.ServerUnreachable] shouldBe (0)

            delay(defaultInterval.toMillis() / 2)

            monitor.stop()

            delay(defaultInterval.toMillis() * 2)

            mockTracker.statistics[MockServerTracker.Statistic.ServerReachable]!! shouldBeGreaterThanOrEqual (3)
            mockTracker.statistics[MockServerTracker.Statistic.ServerUnreachable] shouldBe (0)
        }
    }
})
