package stasis.shared.api.requests

import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

private[stasis] trait UpdateDevice

object UpdateDevice {
  implicit class RequestToUpdatedDevice(request: UpdateDevice) {
    def toUpdatedDevice(device: Device, owner: User): Device =
      request match {
        case UpdateDeviceState(isActive) => device.copy(isActive = isActive)
        case UpdateDeviceLimits(limits)  => device.copy(limits = Device.resolveLimits(owner.limits, limits))
      }
  }
}
