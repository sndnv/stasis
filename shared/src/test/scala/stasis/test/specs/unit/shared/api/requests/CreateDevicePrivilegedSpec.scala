package stasis.test.specs.unit.shared.api.requests

import stasis.core.routing.Node
import stasis.shared.api.requests.CreateDevicePrivileged
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class CreateDevicePrivilegedSpec extends UnitSpec {
  it should "convert requests to devices" in {
    val expectedDevice = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None
    )

    val privilegedRequest = CreateDevicePrivileged(
      name = "test-device",
      node = Some(expectedDevice.node),
      owner = owner.id,
      limits = expectedDevice.limits
    )

    privilegedRequest.toDevice(owner).copy(id = expectedDevice.id) should be(expectedDevice)
  }

  it should "generate random node IDs if none are provided when converting requests to devices" in {
    val expectedDevice = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None
    )

    val privilegedRequest = CreateDevicePrivileged(
      name = "test-device",
      node = None,
      owner = owner.id,
      limits = expectedDevice.limits
    )

    val actualDevice = privilegedRequest.toDevice(owner).copy(id = expectedDevice.id)
    actualDevice should be(expectedDevice.copy(node = actualDevice.node))
    actualDevice.node should not be (expectedDevice.node)
  }

  private val owner = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty
  )
}
