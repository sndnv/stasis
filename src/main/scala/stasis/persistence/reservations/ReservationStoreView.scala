package stasis.persistence.reservations

import stasis.persistence.CrateStorageReservation

import scala.concurrent.Future

trait ReservationStoreView {
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def reservations(): Future[Seq[CrateStorageReservation]]
}
