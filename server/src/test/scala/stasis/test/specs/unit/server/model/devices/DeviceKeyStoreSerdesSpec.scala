package stasis.test.specs.unit.server.model.devices

import stasis.server.model.devices.DeviceKeyStoreSerdes
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.shared.model.Generators

class DeviceKeyStoreSerdesSpec extends UnitSpec {
  "DeviceKeyStoreSerdes" should "serialize and deserialize keys" in {
    val device = Device.generateId()

    val serialized = DeviceKeyStoreSerdes.serializeKey(device)
    val deserialized = DeviceKeyStoreSerdes.deserializeKey(serialized)

    deserialized should be(device)
  }

  they should "serialize and deserialize values" in {
    val deviceKey = Generators.generateDeviceKey

    val serialized = DeviceKeyStoreSerdes.serializeValue(deviceKey)
    val deserialized = DeviceKeyStoreSerdes.deserializeValue(serialized)

    deserialized should be(deviceKey)
  }
}
