package stasis.test.client_android.lib.api.clients.caching

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.caching.CacheRefreshHandler
import stasis.client_android.lib.api.clients.caching.DatasetEntriesForDefinition
import stasis.client_android.lib.api.clients.caching.DefaultCacheRefreshHandler
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Try
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.time.Duration
import java.util.UUID

class DefaultCacheRefreshHandlerSpec : WordSpec({
    "A DefaultCacheRefreshHandler" should {
        fun createHandler(
            underlying: ServerApiEndpointClient = MockServerApiEndpointClient(),
            datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition> = Cache.Map(),
            datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry> = Cache.Map(),
            datasetEntriesForDefinitionCache: Cache<DatasetDefinitionId, DatasetEntriesForDefinition> = Cache.Map(),
            initialDelay: Duration = Duration.ofSeconds(5),
            activeInterval: Duration = Duration.ofSeconds(10),
            pendingInterval: Duration = Duration.ofSeconds(30),
        ): DefaultCacheRefreshHandler =
            DefaultCacheRefreshHandler(
                underlying = underlying,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = initialDelay,
                activeInterval = activeInterval,
                pendingInterval = pendingInterval,
                coroutineScope = testScope
            )

        "validate its interval config" {
            val e1 = shouldThrow<IllegalArgumentException> {
                createHandler(
                    initialDelay = Duration.ofSeconds(1000),
                    activeInterval = Duration.ofSeconds(5),
                    pendingInterval = Duration.ofSeconds(10),
                )
            }

            val e2 = shouldThrow<IllegalArgumentException> {
                createHandler(
                    initialDelay = Duration.ofSeconds(1),
                    activeInterval = Duration.ofSeconds(10),
                    pendingInterval = Duration.ofSeconds(10),
                )
            }

            e1.message shouldBe ("Initial delay [PT16M40S] must be larger than pending [PT10S] and active [PT5S] intervals")
            e2.message shouldBe ("Pending interval [PT10S] must be larger than active interval [PT10S]")
        }

        "periodically refresh caches" {
            val client = MockServerApiEndpointClient()
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofMillis(100),
                activeInterval = Duration.ofMillis(300),
                pendingInterval = Duration.ofMillis(700),
            )

            delay(200)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldNotBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            delay(300)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldNotBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            eventually {
                handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (2)
                handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)

                client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (2)
                client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
                client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
                client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
                client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

                datasetDefinitionsCache.all().size shouldNotBe (0)
                datasetEntriesCache.all().size shouldBe (0)
                datasetEntriesForDefinitionCache.all().size shouldBe (0)
            }

            handler.stop()
        }

        "support immediately refreshing caches (all dataset definitions)" {
            val client = MockServerApiEndpointClient()
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofSeconds(5),
                activeInterval = Duration.ofSeconds(10),
                pendingInterval = Duration.ofSeconds(20),
            )

            delay(200)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.refreshNow(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldNotBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.refreshNow(target = CacheRefreshHandler.RefreshTarget.AllDatasetDefinitions)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (2)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (2)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldNotBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.stop()
        }

        "support immediately refreshing caches (all dataset entries for a definition)" {
            val client = MockServerApiEndpointClient()
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofSeconds(5),
                activeInterval = Duration.ofSeconds(10),
                pendingInterval = Duration.ofSeconds(20),
            )

            delay(200)

            handler.stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val definition = UUID.randomUUID()

            handler.refreshNow(target = CacheRefreshHandler.RefreshTarget.AllDatasetEntries(definition = definition))

            handler.stats.targets["refreshed_all_dataset_entries"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_all_dataset_entries"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (1)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldNotBe (0)

            val cached = datasetEntriesForDefinitionCache.get(key = definition)
            cached?.entries?.size shouldBe (3)
            cached?.latest shouldNotBe (null)

            handler.stop()
        }

        "support immediately refreshing caches (latest dataset entry for a definition)" {
            val client = MockServerApiEndpointClient()
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofSeconds(5),
                activeInterval = Duration.ofSeconds(10),
                pendingInterval = Duration.ofSeconds(20),
            )

            delay(200)

            handler.stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val definition = UUID.randomUUID()

            handler.refreshNow(target = CacheRefreshHandler.RefreshTarget.LatestDatasetEntry(definition = definition))

            handler.stats.targets["refreshed_latest_dataset_entry"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_latest_dataset_entry"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (1)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (1)

            val cached = datasetEntriesForDefinitionCache.get(key = definition)
            cached?.entries?.size shouldBe (1)
            cached?.latest shouldNotBe (null)

            handler.stop()
        }

        "support immediately refreshing caches (individual dataset definitions)" {
            val client = MockServerApiEndpointClient()
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofSeconds(5),
                activeInterval = Duration.ofSeconds(10),
                pendingInterval = Duration.ofSeconds(20),
            )

            delay(200)

            handler.stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.refreshNow(
                target = CacheRefreshHandler.RefreshTarget.IndividualDatasetDefinition(
                    definition = UUID.randomUUID()
                )
            )

            handler.stats.targets["refreshed_individual_dataset_definition"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_individual_dataset_definition"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldNotBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.stop()
        }

        "support immediately refreshing caches (individual dataset entries)" {
            val client = MockServerApiEndpointClient()
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofSeconds(5),
                activeInterval = Duration.ofSeconds(10),
                pendingInterval = Duration.ofSeconds(20),
            )

            delay(200)

            handler.stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.refreshNow(target = CacheRefreshHandler.RefreshTarget.IndividualDatasetEntry(entry = UUID.randomUUID()))

            handler.stats.targets["refreshed_individual_dataset_entry"]?.successful shouldBe (1)
            handler.stats.targets["refreshed_individual_dataset_entry"]?.failed shouldBe (0)

            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            client.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (1)

            val cached = datasetEntriesForDefinitionCache.all().values.firstOrNull()
            cached?.entries?.size shouldBe (1)
            cached?.latest shouldNotBe (null)

            handler.stop()
        }

        "handle refresh failures" {
            val client = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> {
                    return Try.Failure(RuntimeException("Test failure"))
                }
            }
            val datasetDefinitionsCache = Cache.Map<DatasetDefinitionId, DatasetDefinition>()
            val datasetEntriesCache = Cache.Map<DatasetEntryId, DatasetEntry>()
            val datasetEntriesForDefinitionCache = Cache.Map<DatasetDefinitionId, DatasetEntriesForDefinition>()

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            val handler = createHandler(
                underlying = client,
                datasetDefinitionsCache = datasetDefinitionsCache,
                datasetEntriesCache = datasetEntriesCache,
                datasetEntriesForDefinitionCache = datasetEntriesForDefinitionCache,
                initialDelay = Duration.ofMillis(100),
                activeInterval = Duration.ofMillis(300),
                pendingInterval = Duration.ofMillis(2000),
            )

            delay(200)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (1)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            delay(300)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (2)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            delay(400)

            handler.stats.targets["refreshed_all_dataset_definitions"]?.successful shouldBe (0)
            handler.stats.targets["refreshed_all_dataset_definitions"]?.failed shouldBe (3)

            datasetDefinitionsCache.all().size shouldBe (0)
            datasetEntriesCache.all().size shouldBe (0)
            datasetEntriesForDefinitionCache.all().size shouldBe (0)

            handler.stop()
        }
    }
})
