package stasis.test.specs.unit.shared.interop.devices

import java.util.UUID

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.api.requests.CreateDevicePrivileged
import stasis.shared.api.requests.ReEncryptDeviceSecret
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.shared.interop.InteropSpec

class CreateDeviceInteropSpec extends InteropSpec {
  private val limits = Device.Limits(
    maxCrates = 100L,
    maxStorage = BigInt(1099511627776L),
    maxStoragePerCrate = BigInt(10737418240L),
    maxRetention = 7776000.seconds,
    minRetention = 86400.seconds
  )

  "CreateDevice interop" should "decode and re-encode CreateDevicePrivileged" in {
    assert(
      domain = "devices",
      resource = "CreateDevicePrivileged",
      matches = CreateDevicePrivileged(
        name = "test-device",
        node = Some(UUID.fromString("c1ed6cb7-596b-468c-968c-e4772356d948")),
        owner = UUID.fromString("1528e306-7218-44c2-857c-96c3d64e29b6"),
        limits = Some(limits)
      )
    )
  }

  it should "decode and re-encode CreateDeviceOwn" in {
    assert(
      domain = "devices",
      resource = "CreateDeviceOwn",
      matches = CreateDeviceOwn(
        name = "test-device",
        limits = Some(limits)
      )
    )
  }

  it should "decode and re-encode ReEncryptDeviceSecret" in {
    assert(
      domain = "devices",
      resource = "ReEncryptDeviceSecret",
      matches = ReEncryptDeviceSecret(userPassword = "user-password-value")
    )
  }
}
