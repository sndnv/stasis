package stasis.core.packaging

import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Node

final case class Manifest(
  crate: Crate.Id,
  size: Long,
  copies: Int,
  origin: Node.Id,
  source: Node.Id,
  destinations: Seq[Node.Id]
)

object Manifest {
  def apply(source: Node.Id, reservation: CrateStorageReservation): Manifest =
    Manifest(
      crate = reservation.crate,
      size = reservation.size,
      copies = reservation.copies,
      origin = reservation.origin,
      source = source,
      destinations = Seq.empty
    )

  def apply(
    crate: Crate.Id,
    origin: Node.Id,
    source: Node.Id,
    size: Long,
    copies: Int,
  ): Manifest = Manifest(
    crate = crate,
    size = size,
    copies = copies,
    origin = origin,
    source = source,
    destinations = Seq.empty
  )
}
