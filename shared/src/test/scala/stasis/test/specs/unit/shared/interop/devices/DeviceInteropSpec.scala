package stasis.test.specs.unit.shared.interop.devices

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.responses.CreatedDevice
import stasis.shared.api.responses.DeletedDevice
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.shared.interop.InteropSpec

class DeviceInteropSpec extends InteropSpec {
  "Device interop" should "decode and re-encode a device" in {
    assert(
      domain = "devices",
      resource = "Device",
      matches = Device(
        id = UUID.fromString("f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997"),
        name = "test-device",
        node = UUID.fromString("a9b7d0fb-3751-48fc-8706-9176eca571f1"),
        owner = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
        active = true,
        limits = Some(
          Device.Limits(
            maxCrates = 100L,
            maxStorage = BigInt(1099511627776L),
            maxStoragePerCrate = BigInt(10737418240L),
            maxRetention = 7776000.seconds,
            minRetention = 86400.seconds
          )
        ),
        created = Instant.parse("2026-03-01T12:00:00Z"),
        updated = Instant.parse("2026-03-02T13:00:00Z")
      )
    )
  }

  it should "decode and re-encode a device with null limits" in {
    assert(
      domain = "devices",
      resource = "Device.null-limits",
      matches = Device(
        id = UUID.fromString("f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997"),
        name = "test-device",
        node = UUID.fromString("a9b7d0fb-3751-48fc-8706-9176eca571f1"),
        owner = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
        active = true,
        limits = None,
        created = Instant.parse("2026-03-01T12:00:00Z"),
        updated = Instant.parse("2026-03-02T13:00:00Z")
      )
    )
  }

  it should "decode and re-encode a device without limits" in {
    assert(
      domain = "devices",
      resource = "Device.no-limits",
      matches = Device(
        id = UUID.fromString("f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997"),
        name = "test-device",
        node = UUID.fromString("a9b7d0fb-3751-48fc-8706-9176eca571f1"),
        owner = UUID.fromString("7a1c27c2-0b3c-4f23-a68b-814084bfee7d"),
        active = true,
        limits = None,
        created = Instant.parse("2026-03-01T12:00:00Z"),
        updated = Instant.parse("2026-03-02T13:00:00Z")
      )
    )
  }

  it should "decode and re-encode CreatedDevice" in {
    assert(
      domain = "devices",
      resource = "CreatedDevice",
      matches = CreatedDevice(
        device = UUID.fromString("344f2a22-f52e-4df0-88b8-ecb42ed54478"),
        node = UUID.fromString("6566529b-1ddf-441d-aa2c-eab318f83bcf")
      )
    )
  }

  it should "decode and re-encode DeletedDevice" in {
    assert(
      domain = "devices",
      resource = "DeletedDevice",
      matches = DeletedDevice(existing = true)
    )
  }
}
