package stasis.test.specs.unit.shared.interop.devices

import java.time.Instant
import java.util.UUID

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.test.specs.unit.shared.interop.InteropSpec

class DeviceBootstrapCodeInteropSpec extends InteropSpec {
  "DeviceBootstrapCode interop" should "decode and re-encode an existing-device target" in {
    assert(
      domain = "devices",
      resource = "DeviceBootstrapCode.existing",
      matches = DeviceBootstrapCode(
        id = UUID.fromString("4761f7ef-fe12-49a2-ad6b-5a4894390975"),
        value = "bootstrap-code-value",
        owner = UUID.fromString("1528e306-7218-44c2-857c-96c3d64e29b6"),
        target = Left(UUID.fromString("344f2a22-f52e-4df0-88b8-ecb42ed54478")),
        expiresAt = Instant.parse("2026-06-01T10:00:00Z")
      )
    )
  }

  it should "decode and re-encode a new-device target" in {
    assert(
      domain = "devices",
      resource = "DeviceBootstrapCode.new",
      matches = DeviceBootstrapCode(
        id = UUID.fromString("89e2d96c-d29d-4b01-bb81-8bdae16e7624"),
        value = "bootstrap-code-value",
        owner = UUID.fromString("1528e306-7218-44c2-857c-96c3d64e29b6"),
        target = Right(CreateDeviceOwn(name = "new-device", limits = None)),
        expiresAt = Instant.parse("2026-06-01T10:00:00Z")
      )
    )
  }
}
