package stasis.test.specs.unit.shared.api.requests

import org.apache.pekko.util.ByteString
import stasis.core.routing.Node
import stasis.shared.api.requests.UpdateDeviceKey.RequestToDeviceKey
import stasis.shared.model.devices.{Device, DeviceKey}
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class UpdateDeviceKeySpec extends UnitSpec {
  it should "convert requests to updated devices" in {
    val owner = User(
      id = User.generateId(),
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty
    )

    val device = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None
    )

    val key = ByteString("test-key")

    val expectedDeviceKey = DeviceKey(value = key, owner = owner.id, device = device.id)

    key.toDeviceKey(device, owner) should be(expectedDeviceKey)
  }
}
