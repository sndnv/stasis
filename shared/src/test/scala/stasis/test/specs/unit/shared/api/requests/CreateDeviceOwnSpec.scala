package stasis.test.specs.unit.shared.api.requests

import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class CreateDeviceOwnSpec extends UnitSpec {
  it should "convert requests to devices and nodes" in {
    val ownRequest = CreateDeviceOwn(
      name = "test-device",
      limits = None
    )

    val (actualDevice, actualNode) = ownRequest.toDeviceAndNode(owner)

    actualDevice.name should be(ownRequest.name)
    actualDevice.owner should be(owner.id)
    actualDevice.active should be(true)
    actualDevice.limits should be(ownRequest.limits)
    actualNode.id should be(actualDevice.node)
    actualNode.storageAllowed should be(false)
  }

  private val owner = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty
  )
}
