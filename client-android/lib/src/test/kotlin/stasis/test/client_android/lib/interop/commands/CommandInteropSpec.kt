package stasis.test.client_android.lib.interop.commands

import stasis.client_android.lib.model.server.api.responses.CommandAsJson
import stasis.test.client_android.lib.interop.InteropSpec
import java.time.Instant
import java.util.UUID

class CommandInteropSpec : InteropSpec({
    "Command interop" should {
        "decode and re-encode a logout_user command" {
            assert(
                domain = "commands",
                resource = "Command.logout-user",
                matches = CommandAsJson(
                    sequenceId = 42L,
                    source = "user",
                    target = UUID.fromString("4e167c18-0808-4f23-886c-598e094a1b6b"),
                    parameters = CommandAsJson.CommandParametersAsJson(
                        logoutUser = CommandAsJson.LogoutUserCommandAsJson(reason = "session timed out")
                    ),
                    created = Instant.parse("2026-04-15T10:00:00Z")
                )
            )
        }

        "decode and re-encode a logout_user command without reason" {
            assert(
                domain = "commands",
                resource = "Command.logout-user-no-reason",
                matches = CommandAsJson(
                    sequenceId = 7L,
                    source = "service",
                    target = null,
                    parameters = CommandAsJson.CommandParametersAsJson(
                        logoutUser = CommandAsJson.LogoutUserCommandAsJson(reason = null)
                    ),
                    created = Instant.parse("2026-04-15T10:00:00Z")
                )
            )
        }

        "decode and re-encode an empty command" {
            assert(
                domain = "commands",
                resource = "Command.empty",
                matches = CommandAsJson(
                    sequenceId = 0L,
                    source = "service",
                    target = null,
                    parameters = CommandAsJson.CommandParametersAsJson(logoutUser = null),
                    created = Instant.parse("2026-04-15T10:00:00Z")
                )
            )
        }
    }
})
