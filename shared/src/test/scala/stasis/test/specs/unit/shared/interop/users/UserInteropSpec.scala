package stasis.test.specs.unit.shared.interop.users

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.responses.CreatedUser
import stasis.shared.api.responses.DeletedUser
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.shared.interop.InteropSpec

class UserInteropSpec extends InteropSpec {
  "User interop" should "decode and re-encode a user" in {
    assert(
      domain = "users",
      resource = "User",
      matches = User(
        id = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
        salt = "test-salt-value",
        active = true,
        limits = Some(
          User.Limits(
            maxDevices = 10L,
            maxCrates = 100L,
            maxStorage = BigInt(1099511627776L),
            maxStoragePerCrate = BigInt(10737418240L),
            maxRetention = 7776000.seconds,
            minRetention = 86400.seconds
          )
        ),
        permissions = Set(Permission.View.Self),
        created = Instant.parse("2026-03-01T12:00:00Z"),
        updated = Instant.parse("2026-03-02T13:00:00Z")
      )
    )
  }

  it should "decode and re-encode a user with null limits" in {
    assert(
      domain = "users",
      resource = "User.null-limits",
      matches = User(
        id = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
        salt = "test-salt-value",
        active = true,
        limits = None,
        permissions = Set(Permission.View.Self),
        created = Instant.parse("2026-03-01T12:00:00Z"),
        updated = Instant.parse("2026-03-02T13:00:00Z")
      )
    )
  }

  it should "decode and re-encode a user without limits" in {
    assert(
      domain = "users",
      resource = "User.no-limits",
      matches = User(
        id = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
        salt = "test-salt-value",
        active = true,
        limits = None,
        permissions = Set(Permission.View.Self),
        created = Instant.parse("2026-03-01T12:00:00Z"),
        updated = Instant.parse("2026-03-02T13:00:00Z")
      )
    )
  }

  it should "decode and re-encode CreatedUser" in {
    assert(
      domain = "users",
      resource = "CreatedUser",
      matches = CreatedUser(user = UUID.fromString("1528e306-7218-44c2-857c-96c3d64e29b6"))
    )
  }

  it should "decode and re-encode DeletedUser" in {
    assert(
      domain = "users",
      resource = "DeletedUser",
      matches = DeletedUser(existing = true)
    )
  }
}
