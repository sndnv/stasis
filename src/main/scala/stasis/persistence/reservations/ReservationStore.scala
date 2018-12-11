package stasis.persistence.reservations

import akka.Done
import stasis.persistence.CrateStorageReservation
import stasis.persistence.CrateStorageReservation.Id

import scala.concurrent.Future

trait ReservationStore { store =>
  def put(reservation: CrateStorageReservation): Future[Done]
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def reservations(): Future[Seq[CrateStorageReservation]]

  def view: ReservationStoreView = new ReservationStoreView {
    override def get(reservation: Id): Future[Option[CrateStorageReservation]] = store.get(reservation)
    override def reservations(): Future[Seq[CrateStorageReservation]] = store.reservations()
  }
}
