package stasis.persistence

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageReservation(
  id: CrateStorageReservation.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration,
  expiration: FiniteDuration
)

object CrateStorageReservation {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
