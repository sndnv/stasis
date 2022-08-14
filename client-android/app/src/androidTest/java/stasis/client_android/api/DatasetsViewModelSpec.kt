package stasis.client_android.api

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.Fixtures
import stasis.client_android.await
import stasis.client_android.eventually
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.ops.search.Search
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.utils.Reference
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.mocks.*
import stasis.client_android.providers.ProviderContext
import stasis.client_android.tracking.TrackerViews
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class DatasetsViewModelSpec {
    @Test
    fun createDatasetDefinitions() {
        val definitionCreated = AtomicBoolean(false)

        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.createDefinition(
                request = CreateDatasetDefinition(
                    info = "test-info",
                    device = UUID.randomUUID(),
                    redundantCopies = 1,
                    existingVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofMillis(1)
                    ),
                    removedVersions = DatasetDefinition.Retention(
                        policy = DatasetDefinition.Retention.Policy.All,
                        duration = Duration.ofMillis(1)
                    )
                ),
                f = { definitionCreated.set(true) }
            )

            eventually {
                assertThat(
                    mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated],
                    equalTo(1)
                )
            }
        }
    }

    @Test
    fun provideAllDatasetDefinitions() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.definitions().await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved],
                equalTo(1)
            )

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest],
                equalTo(0)
            )
        }
    }

    @Test
    fun provideAllDatasetDefinitionsThatHaveEntries() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.nonEmptyDefinitions().await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved],
                equalTo(1)
            )

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest],
                equalTo(2)
            )
        }
    }

    @Test
    fun provideIndividualDatasetDefinitions() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.definition(definition = UUID.randomUUID()).await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved],
                equalTo(1)
            )
        }
    }

    @Test
    fun provideDatasetEntriesForDatasetDefinitions() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.entries(forDefinition = UUID.randomUUID()).await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved],
                equalTo(1)
            )
        }
    }

    @Test
    fun provideIndividualDatasetEntries() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.entry(entry = UUID.randomUUID()).await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved],
                equalTo(1)
            )
        }
    }

    @Test
    fun provideLatestDatasetEntry() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            assertThat(
                model.latestEntry().await(),
                notNullValue()
            )

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved],
                equalTo(1)
            )

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest],
                equalTo(2)
            )
        }
    }

    @Test
    fun provideDatasetMetadataForDatasetEntries() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.metadata(forEntry = Generators.generateEntry()).await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved],
                equalTo(1)
            )
        }
    }

    @Test
    fun provideDatasetMetadataForDatasetDefinitions() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            model.metadata(forDefinition = UUID.randomUUID()).await()

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved],
                equalTo(1)
            )

            assertThat(
                mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved],
                equalTo(3)
            )
        }
    }

    @Test
    fun provideSearchResults() {
        val mockApiClient = MockServerApiEndpointClient()
        val model = createModel(mockApiClient)

        runBlocking {
            val result = model.search(query = Regex(".*"), until = null).await()

            assertThat(result.definitions.size, equalTo(2))
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun createModel(
        api: MockServerApiEndpointClient,
    ): DatasetsViewModel {
        val contextFactory = object : ProviderContext.Factory {
            override fun getOrCreate(preferences: SharedPreferences): Reference<ProviderContext> =
                Reference.Singleton(
                    retrieveConfig = { },
                    create = {
                        ProviderContext(
                            core = MockServerCoreEndpointClient(),
                            api = api,
                            search = object : Search {
                                override suspend fun search(
                                    query: Regex,
                                    until: Instant?,
                                ): Try<Search.Result> =
                                    Success(
                                        Search.Result(
                                            definitions = mapOf(
                                                UUID.randomUUID() to Search.DatasetDefinitionResult(
                                                    definitionInfo = "test-info",
                                                    entryId = UUID.randomUUID(),
                                                    entryCreated = Instant.now(),
                                                    matches = emptyMap()
                                                ),
                                                UUID.randomUUID() to Search.DatasetDefinitionResult(
                                                    definitionInfo = "test-info",
                                                    entryId = UUID.randomUUID(),
                                                    entryCreated = Instant.now(),
                                                    matches = emptyMap()
                                                ),
                                            )
                                        )
                                    )
                            },
                            executor = MockOperationExecutor(),
                            trackers = TrackerViews(
                                backup = MockBackupTracker(),
                                recovery = MockRecoveryTracker(),
                                server = MockServerTracker()
                            ),
                            credentials = CredentialsProvider(
                                config = CredentialsProvider.Config(
                                    coreScope = "core",
                                    apiScope = "api",
                                    expirationTolerance = Duration.ZERO
                                ),
                                oAuthClient = MockOAuthClient(),
                                initDeviceSecret = { Fixtures.Secrets.Default },
                                loadDeviceSecret = { Success(Fixtures.Secrets.Default) },
                                storeDeviceSecret = { _, _ -> Success(Fixtures.Secrets.Default) },
                                coroutineScope = CoroutineScope(Dispatchers.IO),
                                getAuthenticationPassword = { Fixtures.Secrets.UserPassword.toHashedAuthenticationPassword() }
                            ),
                            monitor = MockServerMonitor()
                        )
                    },
                    destroy = {}
                )
        }

        return DatasetsViewModel(
            application = ApplicationProvider.getApplicationContext(),
            providerContextFactory = contextFactory
        )
    }
}
