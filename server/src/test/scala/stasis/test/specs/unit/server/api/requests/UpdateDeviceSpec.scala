package stasis.test.specs.unit.server.api.requests

import stasis.core.routing.Node
import stasis.server.api.requests.{UpdateDeviceLimits, UpdateDeviceState}
import stasis.server.model.devices.Device
import stasis.server.model.users.User
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class UpdateDeviceSpec extends UnitSpec {
  it should "convert requests to updated devices" in {
    val owner = User(
      id = User.generateId(),
      isActive = true,
      limits = None,
      permissions = Set.empty
    )

    val initialDevice = Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = owner.id,
      isActive = true,
      limits = None
    )

    val expectedDeviceWithUpdatedState = initialDevice.copy(isActive = false)

    val expectedDeviceWithUpdatedLimits = initialDevice.copy(
      limits = Some(
        Device.Limits(
          maxCrates = 1,
          maxStorage = 2,
          maxStoragePerCrate = 3,
          maxRetention = 4.minutes,
          minRetention = 5.seconds
        )
      )
    )

    val updateStateRequest = UpdateDeviceState(isActive = expectedDeviceWithUpdatedState.isActive)
    val updateLimitsRequest = UpdateDeviceLimits(limits = expectedDeviceWithUpdatedLimits.limits)

    updateStateRequest.toUpdatedDevice(initialDevice, owner) should be(expectedDeviceWithUpdatedState)
    updateLimitsRequest.toUpdatedDevice(initialDevice, owner) should be(expectedDeviceWithUpdatedLimits)
  }
}
