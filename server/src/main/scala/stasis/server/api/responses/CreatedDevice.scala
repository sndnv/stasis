package stasis.server.api.responses
import stasis.server.model.devices.Device

final case class CreatedDevice(device: Device.Id)
