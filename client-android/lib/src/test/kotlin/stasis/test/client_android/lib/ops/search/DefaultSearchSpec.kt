package stasis.test.client_android.lib.ops.search

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.FilesystemMetadata
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.ops.search.DefaultSearch
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import stasis.test.client_android.lib.model.Generators
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

class DefaultSearchSpec : WordSpec({
    "A DefaultSearch" should {
        "perform searchers on dataset metadata" {
            val matchingDefinition = UUID.randomUUID()
            val nonMatchingDefinition = UUID.randomUUID()
            val missingEntryDefinition = UUID.randomUUID()

            val matchingEntry = UUID.randomUUID()

            val searchTerm = "test-file-name"

            val definitions = listOf(
                Generators.generateDefinition().copy(id = matchingDefinition),
                Generators.generateDefinition().copy(id = nonMatchingDefinition),
                Generators.generateDefinition().copy(id = missingEntryDefinition)
            )

            val matchingFiles = mapOf(
                Paths.get("$searchTerm-01") to FilesystemMetadata.EntityState.New,
                Paths.get("$searchTerm-02") to FilesystemMetadata.EntityState.Updated,
                Paths.get("other-$searchTerm") to FilesystemMetadata.EntityState.Existing(UUID.randomUUID()),
                Paths.get(searchTerm) to FilesystemMetadata.EntityState.New
            )

            val nonMatchingFiles = mapOf(
                Paths.get("other-name") to FilesystemMetadata.EntityState.New
            )

            val mockApiClient = object : MockServerApiEndpointClient(self = UUID.randomUUID()) {
                override suspend fun datasetDefinitions(): Try<List<DatasetDefinition>> {
                    super.datasetDefinitions()
                    return Success(definitions)
                }

                override suspend fun latestEntry(
                    definition: DatasetDefinitionId,
                    until: Instant?
                ): Try<DatasetEntry?> =
                    super.latestEntry(definition, until).map {
                        it?.let { entry ->
                            when (definition) {
                                matchingDefinition -> entry.copy(id = matchingEntry) // match
                                nonMatchingDefinition -> entry
                                else -> null
                            }
                        }
                    }

                override suspend fun datasetMetadata(entry: DatasetEntry): Try<DatasetMetadata> {
                    return if (entry.id == matchingEntry) {
                        super.datasetMetadata(entry).map {
                            it.copy(
                                filesystem = FilesystemMetadata(entities = matchingFiles + nonMatchingFiles)
                            )
                        }
                    } else {
                        super.datasetMetadata(entry)
                    }
                }
            }

            val search = DefaultSearch(api = mockApiClient)

            val result = search.search(
                query = Regex(".*$searchTerm.*"),
                until = null
            )

            result.get().definitions.size shouldBe (definitions.size)

            when (val datasetDefinitionResult = result.get().definitions[matchingDefinition]) {
                null -> fail("Expected result but none was found")
                else -> datasetDefinitionResult.matches.mapKeys { it.toString() } shouldBe (
                        matchingFiles.mapKeys { it.toString() }
                        )
            }

            result.get().definitions[nonMatchingDefinition] shouldBe (null)

            result.get().definitions[missingEntryDefinition] shouldBe (null)

            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest] shouldBe (3)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryCreated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryDeleted] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved] shouldBe (1)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved] shouldBe (2)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserSaltReset] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Ping] shouldBe (0)
            mockApiClient.statistics[MockServerApiEndpointClient.Statistic.Commands] shouldBe (0)
        }
    }
})
