package stasis.shared.model.devices

import java.time.Instant

import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.users.User

final case class DeviceBootstrapCode(
  id: DeviceBootstrapCode.Id,
  value: String,
  owner: User.Id,
  target: Either[Device.Id, CreateDeviceOwn],
  expiresAt: Instant
) {
  lazy val targetInfo: String = target match {
    case Left(device)   => s"existing=${device.toString}"
    case Right(request) => s"new=${request.name}"
  }
}

object DeviceBootstrapCode {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  def apply(value: String, owner: User.Id, device: Device.Id, expiresAt: Instant): DeviceBootstrapCode =
    DeviceBootstrapCode(
      id = generateId(),
      value = value,
      owner = owner,
      target = Left(device),
      expiresAt = expiresAt
    )

  def apply(value: String, owner: User.Id, request: CreateDeviceOwn, expiresAt: Instant): DeviceBootstrapCode =
    DeviceBootstrapCode(
      id = generateId(),
      value = value,
      owner = owner,
      target = Right(request),
      expiresAt = expiresAt
    )
}
