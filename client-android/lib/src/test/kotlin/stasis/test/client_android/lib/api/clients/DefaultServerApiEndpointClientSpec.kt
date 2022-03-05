package stasis.test.client_android.lib.api.clients

import com.squareup.moshi.Types
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Source
import stasis.client_android.lib.api.clients.DefaultServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.DatasetMetadata.Companion.toByteString
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import stasis.test.client_android.lib.model.Generators
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DefaultServerApiEndpointClientSpec : WordSpec({
    "DefaultServerApiEndpointClient" should {
        val apiCredentials =
            HttpCredentials.BasicHttpCredentials(username = "some-user", password = "some-password")

        fun createClient(
            serverApiUrl: String,
            self: DeviceId = UUID.randomUUID()
        ): DefaultServerApiEndpointClient = DefaultServerApiEndpointClient(
            serverApiUrl = serverApiUrl,
            credentials = { apiCredentials },
            self = self,
            decryption = DefaultServerApiEndpointClient.DecryptionContext(
                core = object :
                    MockServerCoreEndpointClient(self = UUID.randomUUID(), crates = emptyMap()) {
                    override suspend fun pull(crate: CrateId): Source =
                        Buffer().writeUtf8("test-crate")
                },
                deviceSecret = { Fixtures.Secrets.Default },
                decoder = object : MockEncryption() {
                    override fun decrypt(
                        source: Source,
                        metadataSecret: DeviceMetadataSecret
                    ): Source =
                        Buffer().write(DatasetMetadata.empty().toByteString())
                }
            )
        )

        fun createServer(withResponse: MockResponse? = null): MockWebServer {
            val server = MockWebServer()
            withResponse?.let { server.enqueue(it) }
            server.start()

            return server
        }

        "create dataset definitions" {
            val expectedDefinition = UUID.randomUUID()

            val api =
                createServer(withResponse = MockResponse().setBody("""{"definition":"$expectedDefinition"}"""))
            val apiClient = createClient(api.url("/").toString())

            val expectedRequest = CreateDatasetDefinition(
                info = "test-definition",
                device = apiClient.self,
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

            val createdDefinition = apiClient.createDatasetDefinition(request = expectedRequest)
                .get()

            createdDefinition.definition shouldBe (expectedDefinition)

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("POST")
            actualRequest.path shouldBe ("/datasets/definitions/own")
            apiClient.moshi.adapter(CreateDatasetDefinition::class.java)
                .fromJson(actualRequest.body) shouldBe (expectedRequest)

            api.shutdown()
        }

        "fail to create dataset definitions if the provided device is not the current device" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            val expectedRequest = CreateDatasetDefinition(
                info = "test-definition",
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

            val e = shouldThrow<IllegalArgumentException> {
                apiClient.createDatasetDefinition(request = expectedRequest).get()
            }

            e.message shouldBe ("Cannot create dataset definition for a different device: [${expectedRequest.device}]")

            api.shutdown()
        }

        "create dataset entries" {
            val expectedEntry = UUID.randomUUID()

            val api =
                createServer(withResponse = MockResponse().setBody("""{"entry":"$expectedEntry"}"""))
            val apiClient = createClient(api.url("/").toString())

            val expectedRequest = CreateDatasetEntry(
                definition = UUID.randomUUID(),
                device = UUID.randomUUID(),
                data = setOf(UUID.randomUUID(), UUID.randomUUID()),
                metadata = UUID.randomUUID()
            )

            val createdEntry = apiClient.createDatasetEntry(request = expectedRequest).get()
            createdEntry.entry shouldBe (expectedEntry)

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("POST")
            actualRequest.path shouldBe ("/datasets/entries/own/for-definition/${expectedRequest.definition}")
            apiClient.moshi.adapter(CreateDatasetEntry::class.java)
                .fromJson(actualRequest.body) shouldBe (expectedRequest)

            api.shutdown()
        }

        "retrieve dataset definitions" {
            val device = UUID.randomUUID()

            val expectedDefinitions = listOf(
                Generators.generateDefinition().copy(device = device),
                Generators.generateDefinition().copy(device = device)
            )

            val api = createServer()

            val apiClient = createClient(api.url("/").toString(), self = device)

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter<List<DatasetDefinition>>(
                        Types.newParameterizedType(
                            List::class.java,
                            DatasetDefinition::class.java
                        )
                    ).toJson(expectedDefinitions)
                )
            )

            apiClient.datasetDefinitions() shouldBe (Success(expectedDefinitions))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/definitions/own")

            api.shutdown()
        }

        "not retrieve dataset definitions not for the current device" {
            val device = UUID.randomUUID()

            val expectedDefinitions = listOf(
                Generators.generateDefinition().copy(),
                Generators.generateDefinition().copy(device = device)
            )

            val api = createServer()

            val apiClient = createClient(api.url("/").toString(), self = device)

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter<List<DatasetDefinition>>(
                        Types.newParameterizedType(
                            List::class.java,
                            DatasetDefinition::class.java
                        )
                    ).toJson(expectedDefinitions)
                )
            )

            val actualDefinitions = apiClient.datasetDefinitions()
            actualDefinitions shouldBe (Success(expectedDefinitions.drop(1)))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/definitions/own")

            api.shutdown()
        }

        "retrieve dataset entries for a dataset definition" {
            val definition = UUID.randomUUID()

            val expectedEntries = listOf(
                Generators.generateEntry(),
                Generators.generateEntry(),
                Generators.generateEntry()
            )

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter<List<DatasetEntry>>(
                        Types.newParameterizedType(
                            List::class.java,
                            DatasetEntry::class.java
                        )
                    ).toJson(expectedEntries)
                )
            )

            val actualEntries = apiClient.datasetEntries(definition = definition)
            actualEntries shouldBe (Success(expectedEntries))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/entries/own/for-definition/$definition")

            api.shutdown()
        }

        "retrieve individual dataset definitions" {
            val device = UUID.randomUUID()
            val expectedDefinition = Generators.generateDefinition().copy(device = device)

            val api = createServer()

            val apiClient = createClient(api.url("/").toString(), self = device)

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(DatasetDefinition::class.java)
                        .toJson(expectedDefinition)
                )
            )

            val actualDefinition = apiClient.datasetDefinition(definition = expectedDefinition.id)
            actualDefinition shouldBe (Success(expectedDefinition))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/definitions/own/${expectedDefinition.id}")

            api.shutdown()
        }

        "not retrieve individual dataset definitions not for the current device" {
            val device = UUID.randomUUID()
            val expectedDefinition = Generators.generateDefinition()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString(), self = device)

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(DatasetDefinition::class.java)
                        .toJson(expectedDefinition)
                )
            )

            val e = shouldThrow<IllegalArgumentException> {
                apiClient.datasetDefinition(definition = expectedDefinition.id).get()
            }

            e.message shouldBe ("Cannot retrieve dataset definition for a different device")

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/definitions/own/${expectedDefinition.id}")

            api.shutdown()
        }

        "retrieve individual dataset entries" {
            val expectedEntry = Generators.generateEntry()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(DatasetEntry::class.java).toJson(expectedEntry)
                )
            )

            val actualEntry = apiClient.datasetEntry(entry = expectedEntry.id)
            actualEntry shouldBe (Success(expectedEntry))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/entries/own/${expectedEntry.id}")

            api.shutdown()
        }

        "retrieve the latest dataset entry for a definition" {
            val definition = UUID.randomUUID()
            val expectedEntry = Generators.generateEntry().copy(definition = definition)

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(DatasetEntry::class.java).toJson(expectedEntry)
                )
            )

            val actualEntry = apiClient.latestEntry(definition = definition, until = null)
            actualEntry shouldBe (Success(expectedEntry))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/entries/own/for-definition/${definition}/latest")

            api.shutdown()
        }

        "handle missing latest dataset entry for a definition" {
            val definition = UUID.randomUUID()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setResponseCode(404)
            )

            val actualEntry = apiClient.latestEntry(definition = definition, until = null)
            actualEntry shouldBe (Success(null))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/entries/own/for-definition/${definition}/latest")

            api.shutdown()
        }

        "retrieve the latest dataset entry for a definition until a timestamp" {
            val definition = UUID.randomUUID()
            val expectedEntry = Generators.generateEntry().copy(definition = definition)

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(DatasetEntry::class.java).toJson(expectedEntry)
                )
            )

            val instant = Instant.now()

            val actualEntry = apiClient.latestEntry(definition = definition, until = instant)
            actualEntry shouldBe (Success(expectedEntry))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/entries/own/for-definition/${definition}/latest?until=$instant")

            api.shutdown()
        }

        "retrieve public schedules" {
            val expectedSchedules = listOf(
                Generators.generateSchedule(),
                Generators.generateSchedule(),
                Generators.generateSchedule()
            )

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter<List<Schedule>>(
                        Types.newParameterizedType(
                            List::class.java,
                            Schedule::class.java
                        )
                    ).toJson(expectedSchedules)
                )
            )

            val actualSchedules = apiClient.publicSchedules()
            actualSchedules shouldBe (Success(expectedSchedules))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/schedules/public")

            api.shutdown()
        }

        "retrieve individual public schedules" {
            val expectedSchedule = Generators.generateSchedule()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(Schedule::class.java).toJson(expectedSchedule)
                )
            )

            val actualSchedule = apiClient.publicSchedule(schedule = expectedSchedule.id)
            actualSchedule shouldBe (Success(expectedSchedule))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/schedules/public/${expectedSchedule.id}")

            api.shutdown()
        }

        "retrieve dataset metadata (with entry ID)" {
            val expectedEntry = Generators.generateEntry()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(DatasetEntry::class.java).toJson(expectedEntry)
                )
            )

            val metadata = apiClient.datasetMetadata(entry = expectedEntry.id)
            metadata shouldBe (Success(DatasetMetadata.empty()))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/datasets/entries/own/${expectedEntry.id}")

            api.shutdown()
        }

        "retrieve dataset metadata (with entry)" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            val metadata = apiClient.datasetMetadata(entry = Generators.generateEntry())
            metadata shouldBe (Success(DatasetMetadata.empty()))

            api.shutdown()
        }

        "retrieve current user" {
            val expectedUser = Generators.generateUser()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(User::class.java).toJson(expectedUser)
                )
            )

            val actualUser = apiClient.user()
            actualUser shouldBe (Success(expectedUser))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/users/self")

            api.shutdown()
        }

        "retrieve current device" {
            val expectedDevice = Generators.generateDevice()

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(Device::class.java).toJson(expectedDevice)
                )
            )

            val actualDevice = apiClient.device()
            actualDevice shouldBe (Success(expectedDevice))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/devices/own/${apiClient.self}")

            api.shutdown()
        }

        "make ping requests" {
            val expectedPing = Ping(id = UUID.randomUUID())

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(Ping::class.java).toJson(expectedPing)
                )
            )

            val actualPing = apiClient.ping()
            actualPing shouldBe (Success(expectedPing))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/service/ping")

            api.shutdown()
        }
    }
})
