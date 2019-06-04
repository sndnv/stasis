package stasis.shared.api.requests

import stasis.shared.model.devices.Device

final case class UpdateDeviceLimits(limits: Option[Device.Limits]) extends UpdateDevice
