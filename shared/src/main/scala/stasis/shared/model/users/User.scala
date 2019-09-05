package stasis.shared.model.users

import stasis.shared.security.Permission

import scala.concurrent.duration.FiniteDuration

final case class User(
  id: User.Id,
  active: Boolean,
  limits: Option[User.Limits],
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
