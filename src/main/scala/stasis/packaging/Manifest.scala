package stasis.packaging

import stasis.persistence.CrateStorageReservation
import stasis.routing.Node

import scala.concurrent.duration.FiniteDuration

final case class Manifest(
  crate: Crate.Id,
  size: Long,
  copies: Int,
  retention: FiniteDuration,
  source: Node.Id,
  destinations: Seq[Node.Id]
)

object Manifest {
  def apply(crate: Crate.Id, source: Node.Id, reservation: CrateStorageReservation): Manifest =
    Manifest(
      crate = crate,
      source = source,
      copies = reservation.copies,
      size = reservation.size,
      retention = reservation.retention,
      destinations = Seq.empty
    )

  def apply(
    crate: Crate.Id,
    source: Node.Id,
    size: Long,
    copies: Int,
    retention: FiniteDuration
  ): Manifest = Manifest(
    crate,
    size,
    copies,
    retention,
    source,
    destinations = Seq.empty
  )
}
