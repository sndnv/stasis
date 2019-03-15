package stasis.test.specs.unit.server.model.devices
import stasis.server.model.devices.Device
import stasis.server.model.users.User
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class DeviceSpec extends UnitSpec {

  private val userLimits = User.Limits(
    maxDevices = 10,
    maxCrates = 20,
    maxStorage = 30,
    maxStoragePerCrate = 40,
    maxRetention = 5.hours,
    minRetention = 6.minutes
  )

  private val deviceLimits = Device.Limits(
    maxCrates = 1,
    maxStorage = 2,
    maxStoragePerCrate = 3,
    maxRetention = 4.minutes,
    minRetention = 5.seconds
  )

  it should "convert user limits to device limits" in {
    Device.userToDeviceLimits(userLimits) should be(
      Device.Limits(
        maxCrates = userLimits.maxCrates,
        maxStorage = userLimits.maxStorage,
        maxStoragePerCrate = userLimits.maxStoragePerCrate,
        maxRetention = userLimits.maxRetention,
        minRetention = userLimits.minRetention
      )
    )
  }

  it should "create minimum device limits" in {
    val otherDeviceLimits = Device.Limits(
      maxCrates = 10,
      maxStorage = 1,
      maxStoragePerCrate = 50,
      maxRetention = 4.seconds,
      minRetention = 5.seconds
    )

    Device.minDeviceLimits(
      deviceLimits,
      otherDeviceLimits
    ) should be(
      Device.Limits(
        maxCrates = deviceLimits.maxCrates,
        maxStorage = otherDeviceLimits.maxStorage,
        maxStoragePerCrate = deviceLimits.maxStoragePerCrate,
        maxRetention = otherDeviceLimits.maxRetention,
        minRetention = deviceLimits.minRetention
      )
    )
  }

  it should "resolve device limits" in {
    Device.resolveLimits(userLimits = Some(userLimits), deviceLimits = Some(deviceLimits)) should be(
      Some(Device.minDeviceLimits(Device.userToDeviceLimits(userLimits), deviceLimits))
    )

    Device.resolveLimits(userLimits = None, deviceLimits = Some(deviceLimits)) should be(
      Some(deviceLimits)
    )

    Device.resolveLimits(userLimits = Some(userLimits), deviceLimits = None) should be(
      Some(Device.userToDeviceLimits(userLimits))
    )

    Device.resolveLimits(userLimits = None, deviceLimits = None) should be(
      None
    )
  }
}
