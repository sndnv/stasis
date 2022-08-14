package stasis.test.client_android.lib.api.clients

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.api.clients.CachedServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.datasets.DatasetEntryId
import stasis.client_android.lib.utils.Cache
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CachedServerApiEndpointClientSpec : WordSpec({
    "A CachedServerApiEndpointClient" should {
        fun createClient(
            underlying: ServerApiEndpointClient = MockServerApiEndpointClient(),
            datasetDefinitionsCache: Cache<DatasetDefinitionId, DatasetDefinition> = Cache.Map(),
            datasetEntriesCache: Cache<DatasetEntryId, DatasetEntry> = Cache.Map(),
            datasetMetadataCache: Cache<DatasetEntryId, DatasetMetadata> = Cache.Map()
        ): CachedServerApiEndpointClient = CachedServerApiEndpointClient(
            underlying = underlying,
            datasetDefinitionsCache = datasetDefinitionsCache,
            datasetEntriesCache = datasetEntriesCache,
            datasetMetadataCache = datasetMetadataCache
        )

        "retrieve and cache all dataset definitions" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.datasetDefinitions()

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

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

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "retrieve and cache individual dataset definitions" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val definition1 = UUID.randomUUID()
            val definition2 = UUID.randomUUID()

            client.datasetDefinition(definition = definition1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.datasetDefinition(definition = definition1)
            client.datasetDefinition(definition = definition1)
            client.datasetDefinition(definition = definition1)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.datasetDefinition(definition = definition2)
            client.datasetDefinition(definition = definition2)
            client.datasetDefinition(definition = definition2)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "create dataset definitions and invalidate existing cache" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.datasetDefinitions()
            client.datasetDefinitions()
            client.datasetDefinitions()

            val definition = UUID.randomUUID()

            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)
            client.datasetDefinition(definition = definition)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

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

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "retrieve dataset entries for a dataset definition (without caching)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())
            client.datasetEntries(definition = UUID.randomUUID())

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "retrieve and cache individual dataset entries" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val entry = UUID.randomUUID()

            client.datasetEntry(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)
            client.datasetEntry(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "retrieve and cache latest dataset entry for a dataset definition (when `until` not provided)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val definition = UUID.randomUUID()

            client.latestEntry(definition = definition, until = null)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "create dataset entries and invalidate existing cache" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val definition = UUID.randomUUID()

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.createDatasetEntry(
                request = CreateDatasetEntry(
                    definition = definition,
                    device = UUID.randomUUID(),
                    data = emptySet(),
                    metadata = UUID.randomUUID()
                )
            )

            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)
            client.latestEntry(definition = definition, until = null)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (2)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
        }

        "retrieve and cache dataset metadata (with entry ID)" {
            val underlying = MockServerApiEndpointClient()

            val client = createClient(underlying = underlying)

            val entry = UUID.randomUUID()

            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)

            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)
            client.datasetMetadata(entry = entry)

            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (1)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (5)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
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
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            underlying.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (5)
        }
    }
})
