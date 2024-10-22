package stasis.shared.api.requests

import java.time.Instant

import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

private[stasis] trait UpdateDevice

object UpdateDevice {
  implicit class RequestToUpdatedDevice(request: UpdateDevice) {
    def toUpdatedDevice(device: Device, owner: User): Device =
      request match {
        case UpdateDeviceState(active) =>
          device.copy(active = active, updated = Instant.now())

        case UpdateDeviceLimits(limits) =>
          device.copy(limits = Device.resolveLimits(owner.limits, limits), updated = Instant.now())
      }
  }
}
