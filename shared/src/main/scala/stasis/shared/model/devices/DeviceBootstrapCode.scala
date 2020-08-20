package stasis.shared.model.devices

import java.time.Instant

import stasis.shared.model.users.User

final case class DeviceBootstrapCode(
  value: String,
  owner: User.Id,
  device: Device.Id,
  expiresAt: Instant
)
