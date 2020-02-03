package stasis.test.specs.unit.shared.api.requests

import stasis.core.routing.Node
import stasis.shared.api.requests.CreateDevicePrivileged
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class CreateDevicePrivilegedSpec extends UnitSpec {
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

    val privilegedRequest = CreateDevicePrivileged(
      node = expectedDevice.node,
      owner = owner.id,
      limits = expectedDevice.limits
    )

    privilegedRequest.toDevice(owner).copy(id = expectedDevice.id) should be(expectedDevice)
  }
}
