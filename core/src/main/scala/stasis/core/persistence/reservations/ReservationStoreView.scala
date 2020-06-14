package stasis.core.persistence.reservations

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Node

import scala.concurrent.Future

trait ReservationStoreView {
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]
  def reservations: Future[Map[CrateStorageReservation.Id, CrateStorageReservation]]
}
