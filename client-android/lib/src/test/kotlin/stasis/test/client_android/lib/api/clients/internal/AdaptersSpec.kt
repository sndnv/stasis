package stasis.test.client_android.lib.api.clients.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.api.clients.internal.Adapters
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
