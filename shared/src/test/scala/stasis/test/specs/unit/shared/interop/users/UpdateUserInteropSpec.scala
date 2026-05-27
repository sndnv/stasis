package stasis.test.specs.unit.shared.interop.users

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.requests.UpdateUserLimits
import stasis.shared.api.requests.UpdateUserPasswordOwn
import stasis.shared.api.requests.UpdateUserPermissions
import stasis.shared.api.requests.UpdateUserSalt
import stasis.shared.api.requests.UpdateUserSaltOwn
import stasis.shared.api.requests.UpdateUserState
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.shared.interop.InteropSpec

class UpdateUserInteropSpec extends InteropSpec {
  "UpdateUser interop" should "decode and re-encode UpdateUserLimits" in {
    assert(
      domain = "users",
      resource = "UpdateUserLimits",
      matches = UpdateUserLimits(
        limits = Some(
          User.Limits(
            maxDevices = 20L,
            maxCrates = 200L,
            maxStorage = BigInt(2199023255552L),
            maxStoragePerCrate = BigInt(21474836480L),
            maxRetention = 15552000.seconds,
            minRetention = 172800.seconds
          )
        )
      )
    )
  }

  it should "decode and re-encode UpdateUserPermissions" in {
    assert(
      domain = "users",
      resource = "UpdateUserPermissions",
      matches = UpdateUserPermissions(permissions = Set(Permission.Manage.Self))
    )
  }

  it should "decode and re-encode UpdateUserSalt" in {
    assert(
      domain = "users",
      resource = "UpdateUserSalt",
      matches = UpdateUserSalt(salt = "new-salt-value")
    )
  }

  it should "decode and re-encode UpdateUserState" in {
    assert(
      domain = "users",
      resource = "UpdateUserState",
      matches = UpdateUserState(active = true)
    )
  }

  it should "decode and re-encode UpdateUserPasswordOwn" in {
    assert(
      domain = "users",
      resource = "UpdateUserPasswordOwn",
      matches = UpdateUserPasswordOwn(
        currentPassword = "current-password",
        newPassword = "new-password"
      )
    )
  }

  it should "decode and re-encode UpdateUserSaltOwn" in {
    assert(
      domain = "users",
      resource = "UpdateUserSaltOwn",
      matches = UpdateUserSaltOwn(
        currentPassword = "current-password",
        newSalt = "new-salt-value"
      )
    )
  }
}
