package stasis.core.persistence.reservations

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.Metrics
import stasis.core.persistence.StoreInitializationResult
import stasis.core.routing.Node
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext

trait ReservationStore { store =>
  def put(reservation: CrateStorageReservation): Future[Done]
  def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]]
  def delete(crate: Crate.Id, node: Node.Id): Future[Boolean]
  def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean]
  def reservations: Future[Map[CrateStorageReservation.Id, CrateStorageReservation]]

  def view: ReservationStoreView =
    new ReservationStoreView {
      override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
        store.get(reservation)

      override def existsFor(crate: Crate.Id, node: Node.Id): Future[Boolean] =
        store.existsFor(crate, node)

      override def reservations: Future[Map[CrateStorageReservation.Id, CrateStorageReservation]] =
        store.reservations
    }
}

object ReservationStore {
  def apply(
    expiration: FiniteDuration,
    backend: KeyValueStore[CrateStorageReservation.Id, CrateStorageReservation]
  )(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): StoreInitializationResult[ReservationStore] = {
    implicit val ec: ExecutionContext = system.executionContext
    val untypedSystem = system.classicSystem

    val metrics = telemetry.metrics[Metrics.ReservationStore]

    val cache: KeyValueStore[(Crate.Id, Node.Id), CrateStorageReservation.Id] =
      MemoryStore[(Crate.Id, Node.Id), CrateStorageReservation.Id](name = "reservations-cache")

    def caching(): Future[Done] =
      backend.entries
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
          metrics.recordReservation(reservation)
          val _ = org.apache.pekko.pattern.after(
            duration = expiration,
            using = untypedSystem.scheduler
          )(delete(reservation.crate, reservation.target))
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

      override def reservations: Future[Map[CrateStorageReservation.Id, CrateStorageReservation]] =
        backend.entries
    }

    StoreInitializationResult(
      store = store,
      init = caching
    )
  }
}
