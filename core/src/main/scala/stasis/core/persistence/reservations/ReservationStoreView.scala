package stasis.core.persistence.reservations

import stasis.core.persistence.CrateStorageReservation
import scala.concurrent.Future

import stasis.core.packaging.Crate
import stasis.core.routing.Node

trait ReservationStoreView {
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]
}
