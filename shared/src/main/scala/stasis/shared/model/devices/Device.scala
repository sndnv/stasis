package stasis.shared.model.devices

import scala.concurrent.duration.FiniteDuration

import stasis.core.routing.Node
import stasis.shared.model.users.User

final case class Device(
  id: Device.Id,
  name: String,
  node: Node.Id,
  owner: User.Id,
  active: Boolean,
  limits: Option[Device.Limits]
)

object Device {

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Limits(
    maxCrates: Long,
    maxStorage: BigInt,
    maxStoragePerCrate: BigInt,
    maxRetention: FiniteDuration,
    minRetention: FiniteDuration
  )

  def userToDeviceLimits(user: User.Limits): Device.Limits =
    Device.Limits(
      maxCrates = user.maxCrates,
      maxStorage = user.maxStorage,
      maxStoragePerCrate = user.maxStoragePerCrate,
      maxRetention = user.maxRetention,
      minRetention = user.minRetention
    )

  def minDeviceLimits(a: Device.Limits, b: Device.Limits): Device.Limits =
    Device.Limits(
      maxCrates = math.min(a.maxCrates, b.maxCrates),
      maxStorage = a.maxStorage.min(b.maxStorage),
      maxStoragePerCrate = a.maxStoragePerCrate.min(b.maxStoragePerCrate),
      maxRetention = a.maxRetention.min(b.maxRetention),
      minRetention = a.minRetention.min(b.minRetention)
    )

  def resolveLimits(
    userLimits: Option[User.Limits],
    deviceLimits: Option[Device.Limits]
  ): Option[Device.Limits] =
    (userLimits, deviceLimits) match {
      case (Some(user), Some(device)) => Some(minDeviceLimits(userToDeviceLimits(user), device))
      case (None, Some(device))       => Some(device)
      case (Some(user), None)         => Some(userToDeviceLimits(user))
      case (None, None)               => None
    }
}
