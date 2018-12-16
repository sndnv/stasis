package stasis.packaging

import stasis.persistence.CrateStorageReservation
import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class Manifest(
  crate: Crate.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration,
  origin: Node.Id,
  source: Node.Id,
  destinations: Seq[Node.Id]
)

object Manifest {
  def apply(crate: Crate.Id, source: Node.Id, reservation: CrateStorageReservation): Manifest =
    Manifest(
      crate = crate,
      size = reservation.size,
      copies = reservation.copies,
      retention = reservation.retention,
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
    retention: FiniteDuration
  ): Manifest = Manifest(
    crate = crate,
    size = size,
    copies = copies,
    retention = retention,
    origin = origin,
    source = source,
    destinations = Seq.empty
  )
}
