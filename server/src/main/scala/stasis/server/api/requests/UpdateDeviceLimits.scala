package stasis.server.api.requests
import stasis.server.model.devices.Device

final case class UpdateDeviceLimits(limits: Option[Device.Limits]) extends UpdateDevice
