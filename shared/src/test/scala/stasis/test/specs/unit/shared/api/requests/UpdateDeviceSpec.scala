package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import scala.concurrent.duration._

import stasis.core.routing.Node
import stasis.shared.api.requests.UpdateDeviceLimits
import stasis.shared.api.requests.UpdateDeviceState
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class UpdateDeviceSpec extends UnitSpec {
  it should "convert requests to updated devices" in {
    val owner = User(
      id = User.generateId(),
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty,
      created = Instant.now(),
      updated = Instant.now()
    )

    val initialDevice = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None,
      created = Instant.now(),
      updated = Instant.now()
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

    updateStateRequest.toUpdatedDevice(initialDevice, owner).copy(updated = expectedDeviceWithUpdatedState.updated) should be(
      expectedDeviceWithUpdatedState
    )
    updateLimitsRequest.toUpdatedDevice(initialDevice, owner).copy(updated = expectedDeviceWithUpdatedLimits.updated) should be(
      expectedDeviceWithUpdatedLimits
    )
  }
}
