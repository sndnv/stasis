package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import org.apache.pekko.util.ByteString

import stasis.core.routing.Node
import stasis.shared.api.requests.UpdateDeviceKey.RequestToDeviceKey
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class UpdateDeviceKeySpec extends UnitSpec {
  it should "convert requests to updated devices" in {
    val now = Instant.now()

    val owner = User(
      id = User.generateId(),
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty,
      created = now,
      updated = now
    )

    val device = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = owner.id,
      active = true,
      limits = None,
      created = now,
      updated = now
    )

    val key = ByteString("test-key")

    val expectedDeviceKey = DeviceKey(
      value = key,
      owner = owner.id,
      device = device.id,
      created = now
    )

    key.toDeviceKey(device, owner).copy(created = expectedDeviceKey.created) should be(expectedDeviceKey)
  }
}
