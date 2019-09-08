package stasis.core.persistence

import stasis.core.packaging.Crate
import stasis.core.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class CrateStorageReservation(
  id: CrateStorageReservation.Id,
  crate: Crate.Id,
  size: Long,
  copies: Int,
  origin: Node.Id,
  target: Node.Id
)

object CrateStorageReservation {
  def apply(
    request: CrateStorageRequest,
    target: Node.Id
  ): CrateStorageReservation = new CrateStorageReservation(
    id = generateId(),
    crate = request.crate,
    size = request.size,
    copies = request.copies,
    origin = request.origin,
    target = target
  )

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
