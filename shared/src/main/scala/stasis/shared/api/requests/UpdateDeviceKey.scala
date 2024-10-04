package stasis.shared.api.requests

import org.apache.pekko.util.ByteString

import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.users.User

object UpdateDeviceKey {
  implicit class RequestToDeviceKey(key: ByteString) {
    def toDeviceKey(device: Device, owner: User): DeviceKey =
      DeviceKey(
        value = key,
        owner = owner.id,
        device = device.id
      )
  }
}
