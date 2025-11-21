package stasis.test.client_android.lib.api.clients.caching

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import stasis.client_android.lib.api.clients.CachedServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.caching.CachingExtensions.refreshDatasetDefinition
import stasis.client_android.lib.api.clients.caching.CachingExtensions.refreshDatasetDefinitions
import stasis.client_android.lib.api.clients.caching.CachingExtensions.refreshDatasetEntries
import stasis.client_android.lib.api.clients.caching.CachingExtensions.refreshDatasetEntry
import stasis.client_android.lib.api.clients.caching.CachingExtensions.refreshLatestDatasetEntry
import stasis.client_android.lib.api.clients.caching.CachingExtensions.statistics
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.api.clients.caching.DefaultCacheRefreshHandler
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.time.Duration
import java.util.UUID

class CachingExtensionsSpec : WordSpec({
    "CachingExtensions" should {
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

        "provide refresh functions caching API client" {
            val underlying = MockServerApiEndpointClient()
            val client = createClient(underlying = underlying)

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

            client.refreshDatasetDefinitions()

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

            client.refreshDatasetEntries(definition = UUID.randomUUID())

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)

            client.refreshDatasetDefinition(definition = UUID.randomUUID())

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)

            client.refreshDatasetEntry(entry = UUID.randomUUID())

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)

            client.refreshLatestDatasetEntry(definition = UUID.randomUUID())

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
        }

        "provide statistics for tracking caches" {
            val statistics = createClient().statistics()

            statistics shouldNotBe (null)
        }

        "do nothing for non-caching API clients" {
            val client = MockServerApiEndpointClient()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)

            client.refreshDatasetDefinitions()
            client.refreshDatasetEntries(definition = UUID.randomUUID())
            client.refreshDatasetDefinition(definition = UUID.randomUUID())
            client.refreshDatasetEntry(entry = UUID.randomUUID())
            client.refreshLatestDatasetEntry(definition = UUID.randomUUID())

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)

            client.statistics() shouldBe (null)
        }
    }
})
