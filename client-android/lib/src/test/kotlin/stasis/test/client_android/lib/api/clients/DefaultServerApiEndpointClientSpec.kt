package stasis.test.client_android.lib.api.clients

import com.squareup.moshi.Types
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.Source
import stasis.client_android.lib.api.clients.DefaultServerApiEndpointClient
import stasis.client_android.lib.api.clients.exceptions.EndpointFailure
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.DatasetMetadata.Companion.toByteString
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.api.requests.CreateAnalyticsEntry
import stasis.client_android.lib.model.server.api.requests.CreateDatasetDefinition
import stasis.client_android.lib.model.server.api.requests.CreateDatasetEntry
import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.model.server.api.requests.UpdateDatasetDefinition
import stasis.client_android.lib.model.server.api.responses.CommandAsJson
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.asProtobuf
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.toJson
import stasis.client_android.lib.model.server.api.responses.Ping
import stasis.client_android.lib.model.server.api.responses.UpdatedUserSalt
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.datasets.DatasetEntry
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.users.User
import stasis.client_android.lib.security.HttpCredentials
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try.Success
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.LogoutUser
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.mocks.MockEncryption
import stasis.test.client_android.lib.mocks.MockServerCoreEndpointClient
import stasis.test.client_android.lib.model.Generators
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Suppress("LargeClass")
class DefaultServerApiEndpointClientSpec : WordSpec({
    "A DefaultServerApiEndpointClient" should {
        val apiCredentials =
            HttpCredentials.BasicHttpCredentials(username = "some-user", password = "some-password")

        fun createClient(
            serverApiUrl: String,
            self: DeviceId = UUID.randomUUID(),
            decryptionContext: DefaultServerApiEndpointClient.DecryptionContext =
                DefaultServerApiEndpointClient.DecryptionContext.Default(
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
        ): DefaultServerApiEndpointClient = DefaultServerApiEndpointClient(
            serverApiUrl = serverApiUrl,
            credentials = { apiCredentials },
            self = self,
            decryption = decryptionContext
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
            actualRequest.path shouldBe ("/v1/datasets/definitions/own")
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

        "update dataset definitions" {
            val expectedDefinition = UUID.randomUUID()

            val api = createServer(withResponse = MockResponse())
            val apiClient = createClient(api.url("/").toString())

            val expectedRequest = UpdateDatasetDefinition(
                info = "test-definition",
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

            apiClient.updateDatasetDefinition(definition = expectedDefinition, request = expectedRequest).get()

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("PUT")
            actualRequest.path shouldBe ("/v1/datasets/definitions/own/${expectedDefinition}")
            apiClient.moshi.adapter(UpdateDatasetDefinition::class.java)
                .fromJson(actualRequest.body) shouldBe (expectedRequest)

            api.shutdown()
        }

        "delete dataset definitions" {
            val expectedDefinition = UUID.randomUUID()

            val api = createServer(withResponse = MockResponse())
            val apiClient = createClient(api.url("/").toString())

            apiClient.deleteDatasetDefinition(definition = expectedDefinition).get()

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("DELETE")
            actualRequest.path shouldBe ("/v1/datasets/definitions/own/${expectedDefinition}")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/for-definition/${expectedRequest.definition}")
            apiClient.moshi.adapter(CreateDatasetEntry::class.java)
                .fromJson(actualRequest.body) shouldBe (expectedRequest)

            api.shutdown()
        }

        "delete dataset entries" {
            val expectedEntry = UUID.randomUUID()

            val api = createServer(withResponse = MockResponse())
            val apiClient = createClient(api.url("/").toString())

            apiClient.deleteDatasetEntry(entry = expectedEntry).get()

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("DELETE")
            actualRequest.path shouldBe ("/v1/datasets/entries/own/$expectedEntry")

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
            actualRequest.path shouldBe ("/v1/datasets/definitions/own")

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
            actualRequest.path shouldBe ("/v1/datasets/definitions/own")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/for-definition/$definition")

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
            actualRequest.path shouldBe ("/v1/datasets/definitions/own/${expectedDefinition.id}")

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
            actualRequest.path shouldBe ("/v1/datasets/definitions/own/${expectedDefinition.id}")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/${expectedEntry.id}")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/for-definition/${definition}/latest")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/for-definition/${definition}/latest")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/for-definition/${definition}/latest?until=$instant")

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
            actualRequest.path shouldBe ("/v1/schedules/public")

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
            actualRequest.path shouldBe ("/v1/schedules/public/${expectedSchedule.id}")

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
            actualRequest.path shouldBe ("/v1/datasets/entries/own/${expectedEntry.id}")

            api.shutdown()
        }

        "retrieve dataset metadata (with entry)" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            val metadata = apiClient.datasetMetadata(entry = Generators.generateEntry())
            metadata shouldBe (Success(DatasetMetadata.empty()))

            api.shutdown()
        }

        "fail to retrieve dataset metadata without a decryption context" {
            val api = createServer()

            val apiClient = createClient(
                serverApiUrl = api.url("/").toString(),
                decryptionContext = DefaultServerApiEndpointClient.DecryptionContext.Disabled
            )

            val e = shouldThrow<IllegalStateException> {
                apiClient.datasetMetadata(entry = Generators.generateEntry()).get()
            }

            e.message shouldBe ("Cannot retrieve dataset metadata; decryption context is disabled")

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
            actualRequest.path shouldBe ("/v1/users/self")

            api.shutdown()
        }

        "reset the current user's salt" {
            val expectedResponse = UpdatedUserSalt(salt = "test-salt")
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter(UpdatedUserSalt::class.java).toJson(expectedResponse)
                )
            )

            val actualResponse = apiClient.resetUserSalt()
            actualResponse shouldBe (Success(expectedResponse))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("PUT")
            actualRequest.path shouldBe ("/v1/users/self/salt")
            actualRequest.body.size shouldBe (0)
        }

        "update the current user's password" {
            val request = ResetUserPassword(rawPassword = "test-password")
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse())

            val actualResponse = apiClient.resetUserPassword(request)
            actualResponse shouldBe (Success(Unit))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("PUT")
            actualRequest.path shouldBe ("/v1/users/self/password")
            actualRequest.body.readUtf8() shouldBe ("""{"raw_password":"test-password"}""")
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
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}")

            api.shutdown()
        }

        "push current device key" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse())

            val actualResponse = apiClient.pushDeviceKey("test-key".toByteArray().toByteString())
            actualResponse shouldBe (Success(Unit))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("PUT")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/key")
            actualRequest.headers["Content-Type"] shouldBe ("application/octet-stream")
            actualRequest.body.readUtf8() shouldBe ("test-key")
        }

        "pull current device key" {
            val expectedKey = "test-key"

            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse().setBody(expectedKey))

            val actualResponse = apiClient.pullDeviceKey()
            actualResponse shouldBe (Success(expectedKey.toByteArray().toByteString()))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/key")
        }

        "fail to pull missing device keys" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse())

            shouldThrow<ResourceMissingFailure> { apiClient.pullDeviceKey().get() }

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/key")
        }

        "check current device key (existing)" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse())

            val actualResponse = apiClient.deviceKeyExists()
            actualResponse shouldBe (Success(true))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("HEAD")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/key")
        }

        "check current device key (missing)" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse().setResponseCode(404))

            val actualResponse = apiClient.deviceKeyExists()
            actualResponse shouldBe (Success(false))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("HEAD")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/key")
        }

        "handle failures when checking device keys" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            api.enqueue(MockResponse().setResponseCode(505))

            shouldThrow<EndpointFailure> { apiClient.deviceKeyExists().get() }

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("HEAD")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/key")
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
            actualRequest.path shouldBe ("/v1/service/ping")

            api.shutdown()
        }

        "retrieve all commands" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            val expectedCommands = listOf(
                Command(
                    sequenceId = 1,
                    source = "user",
                    target = null,
                    parameters = CommandParameters(),
                    created = Instant.now().toEpochMilli()
                ),
                Command(
                    sequenceId = 2,
                    source = "service",
                    target = apiClient.self.asProtobuf(),
                    parameters = CommandParameters(LogoutUser()),
                    created = Instant.now().toEpochMilli()
                ),
            )

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter<List<CommandAsJson>>(
                        Types.newParameterizedType(
                            List::class.java,
                            CommandAsJson::class.java
                        )
                    ).toJson(expectedCommands.map { it.toJson() })
                )
            )

            val actualCommands = apiClient.commands(lastSequenceId = null)
            actualCommands shouldBe (Success(expectedCommands))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/commands")

            api.shutdown()
        }

        "retrieve unprocessed commands" {
            val api = createServer()

            val apiClient = createClient(api.url("/").toString())

            val expectedCommands = listOf(
                Command(
                    sequenceId = 1,
                    source = "user",
                    target = null,
                    parameters = CommandParameters(),
                    created = Instant.now().toEpochMilli()
                ),
                Command(
                    sequenceId = 2,
                    source = "service",
                    target = apiClient.self.asProtobuf(),
                    parameters = CommandParameters(LogoutUser()),
                    created = Instant.now().toEpochMilli()
                ),
            )

            api.enqueue(
                MockResponse().setBody(
                    apiClient.moshi.adapter<List<CommandAsJson>>(
                        Types.newParameterizedType(
                            List::class.java,
                            CommandAsJson::class.java
                        )
                    ).toJson(expectedCommands.map { it.toJson() })
                )
            )

            val actualCommands = apiClient.commands(lastSequenceId = 42)
            actualCommands shouldBe (Success(expectedCommands))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("GET")
            actualRequest.path shouldBe ("/v1/devices/own/${apiClient.self}/commands?last_sequence_id=42")

            api.shutdown()
        }

        "send analytics entries" {
            val expectedEntry = UUID.randomUUID()

            val api = createServer(withResponse = MockResponse().setBody("""{"entry":"$expectedEntry"}"""))

            val apiClient = createClient(api.url("/").toString())

            val expectedRequest = AnalyticsEntry.collected(app = ApplicationInformation.none())

            val result = apiClient.sendAnalyticsEntry(entry = expectedRequest)

            result shouldBe (Success(Unit))

            val actualRequest = api.takeRequest()
            actualRequest.method shouldBe ("POST")
            actualRequest.path shouldBe ("/v1/analytics")

            val actualRequestContent = apiClient.moshi.adapter(CreateAnalyticsEntry::class.java)
                .fromJson(actualRequest.body)

            actualRequestContent?.entry?.asCollected() shouldBe(expectedRequest)

            api.shutdown()
        }
    }
})
