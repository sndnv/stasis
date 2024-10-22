package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import scala.concurrent.duration._

import stasis.shared.api.requests.UpdateUserLimits
import stasis.shared.api.requests.UpdateUserPermissions
import stasis.shared.api.requests.UpdateUserSalt
import stasis.shared.api.requests.UpdateUserState
import stasis.shared.model.users.User
import stasis.shared.security.Permission
import stasis.test.specs.unit.UnitSpec

class UpdateUserSpec extends UnitSpec {
  it should "convert requests to updated users" in {
    val initialUser = User(
      id = User.generateId(),
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty,
      created = Instant.now(),
      updated = Instant.now()
    )

    val expectedUserWithUpdatedState = initialUser.copy(active = false)

    val expectedUserWithUpdatedLimits = initialUser.copy(
      limits = Some(
        User.Limits(
          maxDevices = 1,
          maxCrates = 2,
          maxStorage = 3,
          maxStoragePerCrate = 4,
          maxRetention = 5.minutes,
          minRetention = 6.seconds
        )
      )
    )

    val expectedUserWithUpdatedSalt = initialUser.copy(salt = "other-salt")

    val expectedUserWithUpdatedPermissions = initialUser.copy(permissions = Set(Permission.Manage.Self))

    val updateStateRequest = UpdateUserState(active = expectedUserWithUpdatedState.active)

    val updateLimitsRequest = UpdateUserLimits(limits = expectedUserWithUpdatedLimits.limits)

    val updatePermissionsRequest = UpdateUserPermissions(permissions = expectedUserWithUpdatedPermissions.permissions)

    val updateSaltRequest = UpdateUserSalt(salt = "other-salt")

    updateStateRequest.toUpdatedUser(initialUser).copy(updated = expectedUserWithUpdatedState.updated) should be(
      expectedUserWithUpdatedState
    )
    updateLimitsRequest.toUpdatedUser(initialUser).copy(updated = expectedUserWithUpdatedLimits.updated) should be(
      expectedUserWithUpdatedLimits
    )
    updatePermissionsRequest.toUpdatedUser(initialUser).copy(updated = expectedUserWithUpdatedPermissions.updated) should be(
      expectedUserWithUpdatedPermissions
    )
    updateSaltRequest.toUpdatedUser(initialUser).copy(updated = expectedUserWithUpdatedSalt.updated) should be(
      expectedUserWithUpdatedSalt
    )
  }
}
