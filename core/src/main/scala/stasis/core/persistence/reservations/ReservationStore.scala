package stasis.core.persistence.reservations

import scala.concurrent.Future

import org.apache.pekko.Done

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.routing.Node
import io.github.sndnv.layers.persistence.Store

trait ReservationStore extends Store { store =>
  def put(reservation: CrateStorageReservation): Future[Done]
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def delete(crate: Crate.Id, node: Node.Id): Future[Boolean]
  def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]
  def reservations: Future[Seq[CrateStorageReservation]]

  def view: ReservationStore.View =
    new ReservationStore.View {
      override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
        store.get(reservation)

      override def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean] =
        store.existsFor(crate, node)

      override def reservations: Future[Seq[CrateStorageReservation]] =
        store.reservations
    }
}

object ReservationStore {
  trait View {
    def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
    def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]
    def reservations: Future[Seq[CrateStorageReservation]]
  }
}
