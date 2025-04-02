package stasis.test.client_android.lib.api.clients.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.api.clients.internal.Adapters
import stasis.client_android.lib.discovery.ServiceApiEndpoint
import stasis.client_android.lib.discovery.ServiceDiscoveryResult
import stasis.client_android.lib.model.core.networking.EndpointAddress
import stasis.client_android.lib.model.server.api.responses.CommandAsJson
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class AdaptersSpec : WordSpec({
    "Adapters" should {
        "convert UUID to/from JSON" {
            val uuid = UUID.randomUUID()
            val json = uuid.toString()

            Adapters.ForUuid.toJson(uuid) shouldBe (json)
            Adapters.ForUuid.fromJson(json) shouldBe (uuid)
        }

        "convert Duration to/from JSON" {
            val duration = Duration.ofMinutes(42)
            val json = duration.seconds

            Adapters.ForDuration.toJson(duration) shouldBe (json)
            Adapters.ForDuration.fromJson(json) shouldBe (duration)
        }

        "convert Instant to/from JSON" {
            val instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            val json = instant.toString()

            Adapters.ForInstant.toJson(instant) shouldBe (json)
            Adapters.ForInstant.fromJson(json) shouldBe (instant)
        }

        "convert LocalDateTime to/from JSON" {
            val localDateTime = LocalDateTime.now()
            val json = localDateTime.toString()

            Adapters.ForLocalDateTime.toJson(localDateTime) shouldBe (json)
            Adapters.ForLocalDateTime.fromJson(json) shouldBe (localDateTime)
        }

        "convert BigInteger to/from JSON" {
            val int = BigInteger("12345678901234567890123456789012345678901234567890")
            val json = int.toString()

            Adapters.ForBigInteger.toJson(int) shouldBe (json)
            Adapters.ForBigInteger.fromJson(json) shouldBe (int)
        }

        "convert DatasetDefinition.Retention.Policy to/from JSON" {
            val policies = mapOf<String, Pair<DatasetDefinition.Retention.Policy, Map<String, Any>>>(
                "at-most" to (DatasetDefinition.Retention.Policy.AtMost(3) to mapOf(
                    "policy_type" to "at-most",
                    "versions" to 3
                )),
                "latest-only" to (DatasetDefinition.Retention.Policy.LatestOnly to mapOf("policy_type" to "latest-only")),
                "all" to (DatasetDefinition.Retention.Policy.All to mapOf("policy_type" to "all"))
            )

            policies.forEach { _, (policy, json) ->
                Adapters.ForDatasetDefinitionRetentionPolicy.toJson(policy) shouldBe (json)
                Adapters.ForDatasetDefinitionRetentionPolicy.fromJson(json) shouldBe (policy)
            }

            val e = shouldThrow<IllegalArgumentException> {
                Adapters.ForDatasetDefinitionRetentionPolicy.fromJson(mapOf("policy_type" to "other"))
            }

            e.message shouldBe ("Unexpected policy type provided: [other]")
        }

        "convert EndpointAddress to/from JSON" {
            val httpAddress = EndpointAddress.HttpEndpointAddress(uri = "http://some-address:1234")
            val grpcAddress = EndpointAddress.GrpcEndpointAddress(host = "some-host", port = 1234, tlsEnabled = false)

            val jsonHttpAddress =
                """{"address_type":"http","address":{"uri":"${httpAddress.uri}"}}"""

            val jsonGrpcAddress =
                """{
                    |"address_type":"grpc",
                    |"address":{
                    |"host":"${grpcAddress.host}",
                    |"port":${grpcAddress.port},
                    |"tls_enabled":${grpcAddress.tlsEnabled}
                    |}
                    |}""".trimMargin().replace("\n", "")

            val moshi = com.squareup.moshi.Moshi.Builder().add(Adapters.ForEndpointAddress).build()
            val adapter = moshi.adapter(EndpointAddress::class.java)

            adapter.toJson(httpAddress) shouldBe (jsonHttpAddress)
            adapter.toJson(grpcAddress) shouldBe (jsonGrpcAddress)

            adapter.fromJson(jsonHttpAddress) shouldBe (httpAddress)
            adapter.fromJson(jsonGrpcAddress) shouldBe (grpcAddress)

            val e = shouldThrow<IllegalArgumentException> {
                Adapters.ForEndpointAddress.fromJson(mapOf("address_type" to "other"))
            }

            e.message shouldBe ("Unexpected address type provided: [other]")
        }

        "convert service discovery results to/from JSON" {
            val keepExistingResult = ServiceDiscoveryResult.KeepExisting
            val switchToResult = ServiceDiscoveryResult.SwitchTo(
                endpoints = ServiceDiscoveryResult.Endpoints(
                    api = ServiceApiEndpoint.Api(uri = "test-uri"),
                    core = ServiceApiEndpoint.Core(address = EndpointAddress.HttpEndpointAddress(uri = "test-uri")),
                    discovery = ServiceApiEndpoint.Discovery(uri = "test-uri")
                ),
                recreateExisting = true
            )

            val jsonKeepExistingResult = """{"result":"keep-existing"}"""
            val jsonSwitchToResult =
                """{
                    |"endpoints":{
                    |"api":{"uri":"test-uri"},
                    |"core":{"address":{"address_type":"http","address":{"uri":"test-uri"}}},
                    |"discovery":{"uri":"test-uri"}
                    |},
                    |"recreate_existing":true,
                    |"result":"switch-to"
                    |}""".trimMargin().replace("\n", "")

            val moshi = com.squareup.moshi.Moshi.Builder().add(Adapters.ForServiceDiscoveryResult).build()
            val adapter = moshi.adapter(ServiceDiscoveryResult::class.java)

            adapter.toJson(keepExistingResult) shouldBe (jsonKeepExistingResult)
            adapter.toJson(switchToResult) shouldBe (jsonSwitchToResult)

            adapter.fromJson(jsonKeepExistingResult) shouldBe (keepExistingResult)
            adapter.fromJson(jsonSwitchToResult) shouldBe (switchToResult)

            val e = shouldThrow<IllegalArgumentException> {
                Adapters.ForServiceDiscoveryResult.fromJson(mapOf("result" to "other"))
            }

            e.message shouldBe ("Unexpected result type provided: [other]")
        }

        "convert Command to/from JSON" {
            val emptyCommand = CommandAsJson.CommandParametersAsJson(logoutUser = null)

            val logoutUserCommand = CommandAsJson.CommandParametersAsJson(
                logoutUser = CommandAsJson.LogoutUserCommandAsJson(reason = "test")
            )

            val emptyCommandJson = mapOf("command_type" to "empty")
            val logoutUserCommandJson = mapOf("command_type" to "logout_user", "reason" to "test")

            Adapters.ForCommandParametersAsJson.toJson(emptyCommand) shouldBe (emptyCommandJson)
            Adapters.ForCommandParametersAsJson.fromJson(emptyCommandJson) shouldBe (emptyCommand)

            Adapters.ForCommandParametersAsJson.toJson(logoutUserCommand) shouldBe (logoutUserCommandJson)
            Adapters.ForCommandParametersAsJson.fromJson(logoutUserCommandJson) shouldBe (logoutUserCommand)

            val e = shouldThrow<IllegalArgumentException> {
                Adapters.ForCommandParametersAsJson.fromJson(mapOf("command_type" to "other"))
            }

            e.message shouldBe ("Unexpected command type provided: [other]")
        }
    }
})
