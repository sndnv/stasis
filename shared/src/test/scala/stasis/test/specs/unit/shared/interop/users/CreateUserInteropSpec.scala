package stasis.test.specs.unit.shared.interop.users

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateUser
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.shared.interop.InteropSpec

class CreateUserInteropSpec extends InteropSpec {
  "CreateUser interop" should "decode and re-encode a request" in {
    assert(
      domain = "users",
      resource = "CreateUser",
      matches = CreateUser(
        username = "test",
        rawPassword = "test-password",
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
        permissions = Set(Permission.Manage.Self)
      )
    )
  }
}
