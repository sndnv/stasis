package stasis.persistence.reservations

import stasis.persistence.CrateStorageReservation
import scala.concurrent.Future

import stasis.packaging.Crate
import stasis.routing.Node

trait ReservationStoreView {
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]
}
