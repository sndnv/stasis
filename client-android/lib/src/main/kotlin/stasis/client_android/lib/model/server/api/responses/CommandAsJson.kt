package stasis.client_android.lib.model.server.api.responses

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import stasis.common.proto.Uuid
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.LogoutUser
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class CommandAsJson(
    @Json(name = "sequence_id")
    val sequenceId: Long,
    val source: String,
    val target: UUID?,
    val parameters: CommandParametersAsJson,
    val created: Instant
) {
    fun toModel(): Command =
        Command(
            sequenceId = sequenceId,
            source = source,
            target = target.asProtobuf(),
            parameters = CommandParameters(logoutUser = parameters.logoutUser?.let { LogoutUser(it.reason) }),
            created = created.toEpochMilli(),
        )

    @JsonClass(generateAdapter = true)
    data class CommandParametersAsJson(
        @Json(name = "logout_user")
        val logoutUser: LogoutUserCommandAsJson? = null
    ) {
        fun isEmpty(): Boolean = logoutUser == null
    }

    data class LogoutUserCommandAsJson(val reason: String?)

    companion object {
        fun Command.isEmpty(): Boolean = parameters?.logoutUser == null

        fun Command.toJson(): CommandAsJson =
            CommandAsJson(
                sequenceId = sequenceId,
                source = source,
                target = target.asJava(),
                parameters = when {
                    parameters?.logoutUser != null -> CommandParametersAsJson(
                        logoutUser = LogoutUserCommandAsJson(reason = parameters.logoutUser.reason)
                    )

                    isEmpty() -> CommandParametersAsJson()

                    else -> throw IllegalArgumentException("Unexpected command provided: [$parameters]")
                },
                created = Instant.ofEpochMilli(created),
            )

        fun Command.name(): String =
            when {
                parameters?.logoutUser != null -> "logout_user"
                else -> "unrecognized"
            }

        fun Command.parametersAsString(): String =
            when {
                parameters?.logoutUser != null -> parameters.logoutUser.toString()
                else -> parameters.toString()
            }

        fun Uuid?.asJava(): UUID? = this?.let { UUID(mostSignificantBits, leastSignificantBits) }

        fun UUID?.asProtobuf(): Uuid? = this?.let { Uuid(mostSignificantBits, leastSignificantBits) }
    }
}
