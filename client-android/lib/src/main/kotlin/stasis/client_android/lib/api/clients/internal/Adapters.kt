package stasis.client_android.lib.api.clients.internal

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import stasis.client_android.lib.model.server.api.responses.CommandAsJson
import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

object Adapters {
    object ForUuid {
        @ToJson
        fun toJson(uuid: UUID): String = uuid.toString()

        @FromJson
        fun fromJson(uuid: String): UUID = UUID.fromString(uuid)
    }

    object ForDuration {
        @ToJson
        fun toJson(duration: Duration): Long = duration.seconds

        @FromJson
        fun fromJson(duration: Long): Duration = Duration.ofSeconds(duration)
    }

    object ForInstant {
        @ToJson
        fun toJson(instant: Instant): String = instant.toString()

        @FromJson
        fun fromJson(instant: String): Instant = Instant.parse(instant)
    }

    object ForLocalDateTime {
        @ToJson
        fun toJson(localDateTime: LocalDateTime): String = localDateTime.toString()

        @FromJson
        fun fromJson(localDateTime: String): LocalDateTime = LocalDateTime.parse(localDateTime)
    }

    object ForBigInteger {
        @ToJson
        fun toJson(int: BigInteger): String = int.toString()

        @FromJson
        fun fromJson(int: String): BigInteger = BigInteger(int)
    }

    object ForDatasetDefinitionRetentionPolicy {
        @ToJson
        fun toJson(policy: DatasetDefinition.Retention.Policy): Map<String, Any> =
            when (policy) {
                is DatasetDefinition.Retention.Policy.AtMost -> mapOf(
                    "policy_type" to "at-most",
                    "versions" to policy.versions
                )

                is DatasetDefinition.Retention.Policy.LatestOnly -> mapOf(
                    "policy_type" to "latest-only"
                )

                is DatasetDefinition.Retention.Policy.All -> mapOf(
                    "policy_type" to "all"
                )
            }

        @FromJson
        fun fromJson(policy: Map<String, Any>): DatasetDefinition.Retention.Policy =
            when (val policyType = policy["policy_type"]) {
                "at-most" -> when (val versions = policy["versions"]) {
                    is Int -> DatasetDefinition.Retention.Policy.AtMost(versions = versions)
                    else -> throw IllegalArgumentException("Expected integer for [versions] but [${versions}] provided")
                }

                "latest-only" -> DatasetDefinition.Retention.Policy.LatestOnly

                "all" -> DatasetDefinition.Retention.Policy.All

                else -> throw IllegalArgumentException("Unexpected policy type provided: [$policyType]")
            }
    }

    object ForCommandParametersAsJson {
        @ToJson
        fun toJson(params: CommandAsJson.CommandParametersAsJson): Map<String, Any> =
            when {
                params.logoutUser != null -> mapOf(
                    "command_type" to "logout_user"
                ) + if (params.logoutUser.reason != null) mapOf("reason" to params.logoutUser.reason) else mapOf()

                params.isEmpty() -> mapOf("command_type" to "empty")

                else -> throw IllegalArgumentException("Unexpected command parameters provided: [$params]")
            }

        @FromJson
        fun fromJson(params: Map<String, Any>): CommandAsJson.CommandParametersAsJson =
            when (val commandType = params["command_type"]) {
                "logout_user" -> when (val reason = params["reason"]) {
                    null -> CommandAsJson.CommandParametersAsJson(
                        logoutUser = CommandAsJson.LogoutUserCommandAsJson(reason = null)
                    )

                    is String -> CommandAsJson.CommandParametersAsJson(
                        logoutUser = CommandAsJson.LogoutUserCommandAsJson(reason = reason)
                    )

                    else -> throw IllegalArgumentException("Expected string for [reason] but [${reason}] provided")
                }

                "empty" -> CommandAsJson.CommandParametersAsJson()

                else -> throw IllegalArgumentException("Unexpected command type provided: [$commandType]")
            }
    }
}
