package stasis.test.specs.unit.shared.interop.commands

import java.time.Instant
import java.util.UUID

import stasis.core.commands.proto.Command
import stasis.core.commands.proto.CommandParameters
import stasis.core.commands.proto.CommandSource
import stasis.core.commands.proto.LogoutUser
import stasis.shared.api.Formats._
import stasis.shared.api.responses.DeletedCommand
import stasis.test.specs.unit.shared.interop.InteropSpec

class CommandInteropSpec extends InteropSpec {
  "Command interop" should "decode and re-encode a logout_user command" in {
    assert(
      domain = "commands",
      resource = "Command.logout-user",
      matches = Command(
        sequenceId = 42L,
        source = CommandSource.User,
        target = Some(UUID.fromString("4e167c18-0808-4f23-886c-598e094a1b6b")),
        parameters = LogoutUser(reason = Some("session timed out")),
        created = Instant.parse("2026-04-15T10:00:00Z")
      )
    )
  }

  it should "decode and re-encode a logout_user command without reason" in {
    assert(
      domain = "commands",
      resource = "Command.logout-user-no-reason",
      matches = Command(
        sequenceId = 7L,
        source = CommandSource.Service,
        target = None,
        parameters = LogoutUser(reason = None),
        created = Instant.parse("2026-04-15T10:00:00Z")
      )
    )
  }

  it should "decode and re-encode an empty command" in {
    assert(
      domain = "commands",
      resource = "Command.empty",
      matches = Command(
        sequenceId = 0L,
        source = CommandSource.Service,
        target = None,
        parameters = CommandParameters.Empty,
        created = Instant.parse("2026-04-15T10:00:00Z")
      )
    )
  }

  it should "decode and re-encode DeletedCommand" in {
    assert(
      domain = "commands",
      resource = "DeletedCommand",
      matches = DeletedCommand(existing = true)
    )
  }
}
