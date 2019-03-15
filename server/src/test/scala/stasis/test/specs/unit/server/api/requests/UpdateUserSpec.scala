package stasis.test.specs.unit.server.api.requests

import stasis.server.api.requests.{UpdateUser, UpdateUserLimits, UpdateUserPermissions, UpdateUserState}
import stasis.server.model.users.User
import stasis.server.security.Permission
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class UpdateUserSpec extends UnitSpec {
  it should "convert requests to updated users" in {
    val initialUser = User(
      id = User.generateId(),
      isActive = true,
      limits = None,
      permissions = Set.empty
    )

    val expectedUserWithUpdatedState = initialUser.copy(isActive = false)

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

    val expectedUserWithUpdatedPermissions = initialUser.copy(permissions = Set(Permission.Manage.Self))

    val updateStateRequest = UpdateUserState(isActive = expectedUserWithUpdatedState.isActive)

    val updateLimitsRequest = UpdateUserLimits(limits = expectedUserWithUpdatedLimits.limits)

    val updatePermissionsRequest = UpdateUserPermissions(permissions = expectedUserWithUpdatedPermissions.permissions)

    updateStateRequest.toUpdatedUser(initialUser) should be(expectedUserWithUpdatedState)
    updateLimitsRequest.toUpdatedUser(initialUser) should be(expectedUserWithUpdatedLimits)
    updatePermissionsRequest.toUpdatedUser(initialUser) should be(expectedUserWithUpdatedPermissions)
  }
}
