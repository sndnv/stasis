package stasis.test.client_android.lib.model.server.api.responses

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.server.api.responses.CommandAsJson
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.asJava
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.asProtobuf
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.isEmpty
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.name
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.parametersAsString
import stasis.client_android.lib.model.server.api.responses.CommandAsJson.Companion.toJson
import stasis.common.proto.Uuid
import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.LogoutUser
import java.time.Instant
import java.util.UUID

class CommandAsJsonSpec : WordSpec({
    "A JSON Command" should {
        "support converting to/from its Protobuf representation" {
            val model = Command(
                sequenceId = 0,
                source = "test",
                target = null,
                parameters = CommandParameters(logoutUser = LogoutUser(reason = "test")),
                created = Instant.now().toEpochMilli()
            )

            val json = CommandAsJson(
                sequenceId = 0,
                source = "test",
                target = null,
                parameters = CommandAsJson.CommandParametersAsJson(
                    logoutUser = CommandAsJson.LogoutUserCommandAsJson(reason = "test")
                ),
                created = Instant.ofEpochMilli(model.created)
            )

            model.toJson() shouldBe (json)
            json.toModel() shouldBe (model)
        }

        "support checking if it is empty" {
            val command = Command(
                sequenceId = 0,
                source = "test",
                target = null,
                parameters = CommandParameters(),
                created = Instant.now().toEpochMilli()
            )

            command.isEmpty() shouldBe (true)

            command.copy(parameters = CommandParameters(logoutUser = LogoutUser(reason = null)))
                .isEmpty() shouldBe (false)
        }

        "support converting UUIDs to/from Protobuf" {
            val java = UUID.randomUUID()

            val protobuf = Uuid(
                mostSignificantBits = java.mostSignificantBits, leastSignificantBits = java.leastSignificantBits
            )

            java.asProtobuf() shouldBe (protobuf)
            protobuf.asJava() shouldBe (java)
        }

        "support providing command names" {
            val unrecognizedCommand = Command(
                sequenceId = 0,
                source = "test",
                target = null,
                parameters = CommandParameters(),
                created = Instant.now().toEpochMilli()
            )

            unrecognizedCommand
                .name() shouldBe ("unrecognized")

            unrecognizedCommand.copy(parameters = CommandParameters(logoutUser = LogoutUser()))
                .name() shouldBe ("logout_user")
        }

        "support providing command parameters as string" {
            val unrecognizedCommand = Command(
                sequenceId = 0,
                source = "test",
                target = null,
                parameters = CommandParameters(),
                created = Instant.now().toEpochMilli()
            )

            unrecognizedCommand
                .parametersAsString() shouldBe ("CommandParameters{}")

            unrecognizedCommand.copy(parameters = CommandParameters(logoutUser = LogoutUser()))
                .parametersAsString() shouldBe ("LogoutUser{}")
        }
    }
})
