package stasis.test.client_android.lib.api.clients

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.CachedServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.api.clients.caching.DefaultCacheRefreshHandler
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Cache
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Suppress("LargeClass", "MaxLineLength")
class CachedServerApiEndpointClientSpec : WordSpec({
    "A CachedServerApiEndpointClient" should {
        fun createClient(
            underlying: ServerApiEndpointClient = MockServerApiEndpointClient(),
            datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition> = Cache.Map(),
            datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry> = Cache.Map(),
            datasetEntriesForDefinitionCache: Cache<DatasetDefinitionId, DatasetEntriesForDefinition> = Cache.Map(),
            datasetMetadataCache: Cache<DatasetEntryId, DatasetMetadata> = Cache.Map(),
        ): CachedServerApiEndpointClient = CachedServerApiEndpointClient(
            underlying = underlying,
            datasetDefinitionsCache = datasetDefinitionsCache,
            datasetEntriesCache = datasetEntriesCache,
            datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
            datasetMetadataCache = datasetMetadataCache,
            refreshHandler = object : DefaultCacheRefreshHandler(
                underlying = underlying,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofSeconds(1),
                activeInterval = Duration.ofSeconds(2),
                pendingInterval = Duration.ofSeconds(3),
                coroutineScope = testScope
            ) {
                override suspend fun start() = Unit
                override fun stop() = Unit
            }
        )

        "retrieve and cache all dataset definitions" {
            val underlying = MockServerApiEndpointClient()
            val definitions = Cache.Tracking<DatasetDefinitionId, DatasetDefinition>(Cache.Map())

            val client = createClient(underlying = underlying, datasetDefinitionsCache = definitions)

            client.datasetDefinitions()

            definitions.hits shouldBe (0)
            definitions.misses shouldBe (0) // getting all entries is not a miss

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            definitions.hits shouldBe (0)
            definitions.misses shouldBe (0) // getting all entries is not a miss

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve and cache individual dataset definitions" {
            val underlying = MockServerApiEndpointClient()
            val definitions = Cache.Tracking<DatasetDefinitionId, DatasetDefinition>(Cache.Map())

            val client = createClient(underlying = underlying, datasetDefinitionsCache = definitions)

            val definition1 = UUID.randomUUID()
            val definition2 = UUID.randomUUID()

            client.datasetDefinition(definition = definition1)

            definitions.hits shouldBe (1)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.datasetDefinition(definition = definition1)
            client.datasetDefinition(definition = definition1)
            client.datasetDefinition(definition = definition1)

            definitions.hits shouldBe (4)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.datasetDefinition(definition = definition2)
            client.datasetDefinition(definition = definition2)
            client.datasetDefinition(definition = definition2)

            definitions.hits shouldBe (7)
            definitions.misses shouldBe (2)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "create dataset definitions and invalidate existing cache" {
            val underlying = MockServerApiEndpointClient()
            val definitions = Cache.Tracking<DatasetDefinitionId, DatasetDefinition>(Cache.Map())

            val client = createClient(underlying = underlying, datasetDefinitionsCache = definitions)

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            definitions.hits shouldBe (0)
            definitions.misses shouldBe (0) // getting all entries is not a miss

            val definition = UUID.randomUUID()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            definitions.hits shouldBe (3)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.createDatasetDefinition(
                request = CreateDatasetDefinition(
                    info = "test",
                    device = UUID.randomUUID(),
                    redundantCopies = 1,
                    existingVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofSeconds(3)
                    ),
                    removedVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofSeconds(3)
                    )
                )
            )

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            definitions.hits shouldBe (6)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "update dataset definitions and invalidate existing cache" {
            val underlying = MockServerApiEndpointClient()
            val definitions = Cache.Tracking<DatasetDefinitionId, DatasetDefinition>(Cache.Map())

            val client = createClient(underlying = underlying, datasetDefinitionsCache = definitions)

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            definitions.hits shouldBe (0)
            definitions.misses shouldBe (0) // getting all entries is not a miss

            val definition = UUID.randomUUID()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            definitions.hits shouldBe (3)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.updateDatasetDefinition(
                definition = definition,
                request = UpdateDatasetDefinition(
                    info = "test",
                    redundantCopies = 1,
                    existingVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofSeconds(3)
                    ),
                    removedVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofSeconds(3)
                    )
                )
            )

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            definitions.hits shouldBe (6)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "delete dataset definitions and invalidate existing cache" {
            val underlying = MockServerApiEndpointClient()
            val definitions = Cache.Tracking<DatasetDefinitionId, DatasetDefinition>(Cache.Map())

            val client = createClient(underlying = underlying, datasetDefinitionsCache = definitions)

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            definitions.hits shouldBe (0)
            definitions.misses shouldBe (0) // getting all entries is not a miss

            val definition = UUID.randomUUID()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            definitions.hits shouldBe (3)
            definitions.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.deleteDatasetDefinition(definition = definition)

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            definitions.hits shouldBe (6)
            definitions.misses shouldBe (2)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve dataset entries for a dataset definition" {
            val underlying = MockServerApiEndpointClient()
            val entriesForDefinition =
                Cache.Tracking<DatasetDefinitionId, DatasetEntriesForDefinition>(Cache.Map())

            val client = createClient(
                underlying = underlying,
                datasetEntriesForDefinitionCache = entriesForDefinition
            )

            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())

            entriesForDefinition.hits shouldBe (5)
            entriesForDefinition.misses shouldBe (5)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve and cache individual dataset entries" {
            val underlying = MockServerApiEndpointClient()
            val entries = Cache.Tracking<DatasetEntryId, DatasetEntry>(Cache.Map())

            val client = createClient(underlying = underlying, datasetEntriesCache = entries)

            val entry = UUID.randomUUID()

            client.datasetEntry(entry = entry)

            entries.hits shouldBe (1)
            entries.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)

            entries.hits shouldBe (6)
            entries.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve and cache latest dataset entry for a dataset definition (when `until` not provided)" {
            val underlying = MockServerApiEndpointClient()
            val entriesForDefinition =
                Cache.Tracking<DatasetDefinitionId, DatasetEntriesForDefinition>(Cache.Map())

            val client = createClient(
                underlying = underlying,
                datasetEntriesForDefinitionCache = entriesForDefinition
            )

            val definition = UUID.randomUUID()

            client.latestEntry(definition = definition, until = null)

            entriesForDefinition.hits shouldBe (1)
            entriesForDefinition.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0) // always from cache
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            entriesForDefinition.hits shouldBe (6)
            entriesForDefinition.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0) // always from cache
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve and not cache latest dataset entry for dataset definition (when `until` is provided)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val definition = UUID.randomUUID()
            val until = Instant.now()

            client.latestEntry(definition = definition, until = until)
            client.latestEntry(definition = definition, until = until)
            client.latestEntry(definition = definition, until = until)
            client.latestEntry(definition = definition, until = until)
            client.latestEntry(definition = definition, until = until)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "create dataset entries" {
            val underlying = MockServerApiEndpointClient()
            val entriesForDefinition =
                Cache.Tracking<DatasetDefinitionId, DatasetEntriesForDefinition>(Cache.Map())

            val client = createClient(
                underlying = underlying,
                datasetEntriesForDefinitionCache = entriesForDefinition
            )

            val definition = UUID.randomUUID()

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            entriesForDefinition.hits shouldBe (3)
            entriesForDefinition.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0) // always from cache
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.createDatasetEntry(
                request = CreateDatasetEntry(
                    definition = definition,
                    device = UUID.randomUUID(),
                    data = emptySet(),
                    metadata = UUID.randomUUID()
                )
            )

            entriesForDefinition.hits shouldBe (3)
            entriesForDefinition.misses shouldBe (2)

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            entriesForDefinition.hits shouldBe (6)
            entriesForDefinition.misses shouldBe (2)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0) // always from cache
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "delete dataset entries" {
            val underlying = MockServerApiEndpointClient()
            val entries = Cache.Tracking<DatasetEntryId, DatasetEntry>(Cache.Map())

            val client = createClient(underlying = underlying, datasetEntriesCache = entries)

            val definition = UUID.randomUUID()

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            entries.hits shouldBe (3 * 2) // 3 hits per request; x2 requests
            entries.misses shouldBe (0)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0) // always from cache
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.deleteDatasetEntry(entry = UUID.randomUUID())

            entries.hits shouldBe (3 * 2) // 3 hits per request; x2 requests
            entries.misses shouldBe (1)

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            entries.hits shouldBe (3 * 5) // 3 hits per request; x5 requests
            entries.misses shouldBe (1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0) // always from cache
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve public schedules (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.publicSchedules()
            client.publicSchedules()
            client.publicSchedules()
            client.publicSchedules()
            client.publicSchedules()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve individual public schedules (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val schedule = UUID.randomUUID()

            client.publicSchedule(schedule = schedule)
            client.publicSchedule(schedule = schedule)
            client.publicSchedule(schedule = schedule)
            client.publicSchedule(schedule = schedule)
            client.publicSchedule(schedule = schedule)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve and cache dataset metadata (with entry ID)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val entry = UUID.randomUUID()

            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve and cache dataset metadata (with entry)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val entry = DatasetEntry(
                id = UUID.randomUUID(),
                definition = UUID.randomUUID(),
                device = UUID.randomUUID(),
                data = emptySet(),
                metadata = UUID.randomUUID(),
                created = Instant.now()
            )

            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)

            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve current user (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.user()
            client.user()
            client.user()
            client.user()
            client.user()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "reset the current user's salt (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.resetUserSalt()
            client.resetUserSalt()
            client.resetUserSalt()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (3)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "update the current user's password (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val request = ResetUserPassword(rawPassword = "test")

            client.resetUserPassword(request)
            client.resetUserPassword(request)
            client.resetUserPassword(request)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (3)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve current device (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.device()
            client.device()
            client.device()
            client.device()
            client.device()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "push current device key (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val key = "test-key".toByteArray().toByteString()

            client.pushDeviceKey(key)
            client.pushDeviceKey(key)
            client.pushDeviceKey(key)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (3)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "pull current device key (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.pullDeviceKey()
            client.pullDeviceKey()
            client.pullDeviceKey()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (3)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "check if a device key exists (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.deviceKeyExists()
            client.deviceKeyExists()
            client.deviceKeyExists()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (3)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "make ping requests (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.ping()
            client.ping()
            client.ping()
            client.ping()
            client.ping()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "retrieve commands (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.commands(lastSequenceId = null)
            client.commands(lastSequenceId = 42)
            client.commands(lastSequenceId = null)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (3)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (0)
        }

        "send analytics entries (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.sendAnalyticsEntry(entry = AnalyticsEntry.collected(app = ApplicationInformation.none()))
            client.sendAnalyticsEntry(entry = AnalyticsEntry.collected(app = ApplicationInformation.none()))
            client.sendAnalyticsEntry(entry = AnalyticsEntry.collected(app = ApplicationInformation.none()))

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent] shouldBe (3)
        }
    }
})
