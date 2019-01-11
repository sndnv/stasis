package stasis.persistence.reservations

import scala.concurrent.{ExecutionContext, Future}

import akka.Done
import stasis.packaging.Crate
import stasis.persistence.CrateStorageReservation
import stasis.persistence.backends.KeyValueBackend
import stasis.routing.Node

trait ReservationStore { store =>
  def put(reservation: CrateStorageReservation): Future[Done]
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def delete(crate: Crate.Id, node: Node.Id): Future[Boolean]
  def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]

  def view: ReservationStoreView = new ReservationStoreView {
    override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
      store.get(reservation)

    override def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean] =
      store.existsFor(crate, node)
  }
}

object ReservationStore {
  def apply(
    backend: KeyValueBackend[CrateStorageReservation.Id, CrateStorageReservation],
    cache: KeyValueBackend[(Crate.Id, Node.Id), CrateStorageReservation.Id]
  )(implicit ec: ExecutionContext): ReservationStore =
    new ReservationStore {
      override def put(reservation: CrateStorageReservation): Future[Done] =
        for {
          _ <- backend.put(reservation.id, reservation)
          _ <- cache.put((reservation.crate, reservation.target), reservation.id)
        } yield {
          Done
        }

      override def delete(crate: Crate.Id, node: Node.Id): Future[Boolean] =
        cache.get((crate, node)).flatMap {
          case Some(reservation) =>
            for {
              _ <- cache.delete((crate, node))
              result <- backend.delete(reservation)
            } yield {
              result
            }

          case None =>
            Future.successful(false)
        }

      override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
        backend.get(reservation)

      override def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean] =
        cache.get((crate, node)).map(_.isDefined)
    }
}
