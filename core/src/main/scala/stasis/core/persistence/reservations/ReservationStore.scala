package stasis.core.persistence.reservations

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.packaging.Crate
import stasis.core.persistence.{CrateStorageReservation, StoreInitializationResult}
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.routing.Node

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

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
    expiration: FiniteDuration,
    backend: KeyValueBackend[CrateStorageReservation.Id, CrateStorageReservation]
  )(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout): StoreInitializationResult[ReservationStore] = {
    implicit val ec: ExecutionContext = system.executionContext

    val cache: KeyValueBackend[(Crate.Id, Node.Id), CrateStorageReservation.Id] =
      MemoryBackend[(Crate.Id, Node.Id), CrateStorageReservation.Id](
        name = s"reservations-cache-${java.util.UUID.randomUUID()}"
      )

    val caching: Future[Done] = backend.entries
      .flatMap { entries =>
        Future
          .sequence(
            entries.values
              .map(reservation => cache.put((reservation.crate, reservation.target), reservation.id))
          )
          .map(_ => Done)
      }

    val store: ReservationStore = new ReservationStore {
      override def put(reservation: CrateStorageReservation): Future[Done] =
        for {
          _ <- backend.put(reservation.id, reservation)
          _ <- cache.put((reservation.crate, reservation.target), reservation.id)
        } yield {
          val _ = akka.pattern.after(expiration, system.scheduler)(delete(reservation.crate, reservation.target))
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

    StoreInitializationResult(
      store = store,
      init = caching
    )
  }
}
