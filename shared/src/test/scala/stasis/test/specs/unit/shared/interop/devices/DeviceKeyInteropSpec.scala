package stasis.test.specs.unit.shared.interop.devices

import java.time.Instant
import java.util.UUID

import org.apache.pekko.util.ByteString

import stasis.shared.api.Formats._
import stasis.shared.api.responses.DeletedDeviceKey
import stasis.shared.model.devices.DeviceKey
import stasis.test.specs.unit.shared.interop.InteropSpec

class DeviceKeyInteropSpec extends InteropSpec {
  "DeviceKey interop" should "decode and re-encode a device key" in {
    assert(
      domain = "devices",
      resource = "DeviceKey",
      matches = DeviceKey(
        value = ByteString.empty,
        owner = UUID.fromString("1528e306-7218-44c2-857c-96c3d64e29b6"),
        device = UUID.fromString("344f2a22-f52e-4df0-88b8-ecb42ed54478"),
        created = Instant.parse("2026-05-01T09:00:00Z")
      )
    )
  }

  it should "decode and re-encode DeletedDeviceKey" in {
    assert(
      domain = "devices",
      resource = "DeletedDeviceKey",
      matches = DeletedDeviceKey(existing = true)
    )
  }
}
