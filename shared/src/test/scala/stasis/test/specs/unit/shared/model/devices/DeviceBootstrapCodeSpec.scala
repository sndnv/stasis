package stasis.test.specs.unit.shared.model.devices

import java.time.Instant

import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class DeviceBootstrapCodeSpec extends UnitSpec {
  it should "provide target info (existing device)" in {
    val deviceId = Device.generateId()

    val existingDeviceCode = DeviceBootstrapCode(
      value = "test-code",
      owner = User.generateId(),
      device = deviceId,
      expiresAt = Instant.now()
    )

    existingDeviceCode.targetInfo should be(s"existing=$deviceId")
  }

  it should "provide target info (new device)" in {
    val request = CreateDeviceOwn(name = "test-name", limits = None)

    val newDeviceCode = DeviceBootstrapCode(
      value = "test-code",
      owner = User.generateId(),
      request = request,
      expiresAt = Instant.now()
    )

    newDeviceCode.targetInfo should be(s"new=${request.name}")
  }
}
