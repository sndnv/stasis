package stasis.test.specs.unit.shared.api.requests

import stasis.core.routing.Node
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class CreateDeviceOwnSpec extends UnitSpec {
  it should "convert requests to devices" in {
    val owner = User(
      id = User.generateId(),
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty
    )

    val expectedDevice = Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None
    )

    val ownRequest = CreateDeviceOwn(
      node = expectedDevice.node,
      limits = expectedDevice.limits
    )

    ownRequest.toDevice(owner).copy(id = expectedDevice.id) should be(expectedDevice)
  }
}
