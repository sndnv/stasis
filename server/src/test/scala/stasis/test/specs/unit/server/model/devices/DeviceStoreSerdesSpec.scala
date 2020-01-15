package stasis.test.specs.unit.server.model.devices

import stasis.server.model.devices.DeviceStoreSerdes
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.shared.model.Generators

class DeviceStoreSerdesSpec extends UnitSpec {
  "DeviceStoreSerdes" should "serialize and deserialize keys" in {
    val device = Device.generateId()

    val serialized = DeviceStoreSerdes.serializeKey(device)
    val deserialized = DeviceStoreSerdes.deserializeKey(serialized)

    deserialized should be(device)
  }

  they should "serialize and deserialize values" in {
    val device = Generators.generateDevice

    val serialized = DeviceStoreSerdes.serializeValue(device)
    val deserialized = DeviceStoreSerdes.deserializeValue(serialized)

    deserialized should be(device)
  }
}
