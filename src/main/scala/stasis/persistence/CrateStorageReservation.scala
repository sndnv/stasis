package stasis.persistence

import stasis.packaging.Crate
import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageReservation(
  id: CrateStorageReservation.Id,
  crate: Crate.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration,
  origin: Node.Id,
  expiration: FiniteDuration
)

object CrateStorageReservation {
  def apply(
    request: CrateStorageRequest,
    expiration: FiniteDuration
  ): CrateStorageReservation = new CrateStorageReservation(
    id = generateId(),
    crate = request.crate,
    size = request.size,
    copies = request.copies,
    retention = request.retention,
    origin = request.origin,
    expiration = expiration
  )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
