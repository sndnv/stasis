package stasis.persistence.reservations

import akka.Done
import stasis.packaging.Crate
import stasis.persistence.CrateStorageReservation

import scala.concurrent.Future

trait ReservationStore { store =>
  def put(reservation: CrateStorageReservation): Future[Done]
  def delete(crate: Crate.Id): Future[Boolean]
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def existsFor(crate: Crate.Id): Future[Boolean]

  def view: ReservationStoreView = new ReservationStoreView {
    override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
      store.get(reservation)
  }
}
