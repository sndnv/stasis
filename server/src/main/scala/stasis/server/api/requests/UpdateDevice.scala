package stasis.server.api.requests

import stasis.server.model.devices.Device
import stasis.server.model.users.User

private[api] trait UpdateDevice

object UpdateDevice {
  implicit class RequestToUpdatedDevice(request: UpdateDevice) {
    def toUpdatedDevice(device: Device, owner: User): Device =
      request match {
        case UpdateDeviceState(isActive) => device.copy(isActive = isActive)
        case UpdateDeviceLimits(limits)  => device.copy(limits = Device.resolveLimits(owner.limits, limits))
      }
  }
}
