package stasis.test.specs.unit.shared.api.requests

import stasis.core.routing.Node
import stasis.shared.api.requests.{UpdateDeviceLimits, UpdateDeviceState}
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class UpdateDeviceSpec extends UnitSpec {
  it should "convert requests to updated devices" in {
    val owner = User(
      id = User.generateId(),
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty
    )

    val initialDevice = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None
    )

    val expectedDeviceWithUpdatedState = initialDevice.copy(active = false)

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

    val updateStateRequest = UpdateDeviceState(active = expectedDeviceWithUpdatedState.active)
    val updateLimitsRequest = UpdateDeviceLimits(limits = expectedDeviceWithUpdatedLimits.limits)

    updateStateRequest.toUpdatedDevice(initialDevice, owner) should be(expectedDeviceWithUpdatedState)
    updateLimitsRequest.toUpdatedDevice(initialDevice, owner) should be(expectedDeviceWithUpdatedLimits)
  }
}
