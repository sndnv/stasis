package stasis.test.specs.unit.shared.interop.devices

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.requests.UpdateDeviceLimits
import stasis.shared.api.requests.UpdateDeviceState
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.shared.interop.InteropSpec

class UpdateDeviceInteropSpec extends InteropSpec {
  "UpdateDevice interop" should "decode and re-encode UpdateDeviceLimits" in {
    assert(
      domain = "devices",
      resource = "UpdateDeviceLimits",
      matches = UpdateDeviceLimits(
        limits = Some(
          Device.Limits(
            maxCrates = 200L,
            maxStorage = BigInt(2199023255552L),
            maxStoragePerCrate = BigInt(21474836480L),
            maxRetention = 15552000.seconds,
            minRetention = 172800.seconds
          )
        )
      )
    )
  }

  it should "decode and re-encode UpdateDeviceState" in {
    assert(
      domain = "devices",
      resource = "UpdateDeviceState",
      matches = UpdateDeviceState(active = false)
    )
  }
}
