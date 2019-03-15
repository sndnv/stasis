package stasis.server.model.users

import stasis.server.security.Permission
import stasis.server.model.users.User.Limits

import scala.concurrent.duration.FiniteDuration

final case class User(
  id: User.Id,
  isActive: Boolean,
  limits: Option[Limits],
  permissions: Set[Permission]
)

object User {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()

  final case class Limits(
    maxDevices: Long,
    maxCrates: Long,
    maxStorage: BigInt,
    maxStoragePerCrate: BigInt,
    maxRetention: FiniteDuration,
    minRetention: FiniteDuration
  )
}
