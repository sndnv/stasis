package stasis.test.specs.unit.core.persistence.mocks

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node

class MockReservationStore(
  missingReservations: Seq[CrateStorageReservation] = Seq.empty,
  ignoreMissingReservations: Boolean = true
)(implicit system: ActorSystem[SpawnProtocol])
    extends ReservationStore {
  private type StoreKey = CrateStorageReservation.Id
  private type StoreValue = CrateStorageReservation

  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  private val store = MemoryBackend.typed[StoreKey, StoreValue](
    name = s"mock-reservation-store-${java.util.UUID.randomUUID()}"
  )

  override def put(reservation: StoreValue): Future[Done] = store.put(reservation.id, reservation)

  override def delete(crate: CrateStorageReservation.Id, node: Node.Id): Future[Boolean] =
    reservations.flatMap { reservations =>
      reservations.find(r => r._2.crate == crate && r._2.target == node) match {
        case Some((_, reservation)) =>
          store.delete(reservation.id)

        case None =>
          if (ignoreMissingReservations) {
            Future.successful(true)
          } else {
            Future.successful(false)
          }
      }
    }

  override def get(reservation: CrateStorageReservation.Id): Future[Option[StoreValue]] =
    if (missingReservations.map(_.id).contains(reservation)) {
      Future.successful(None)
    } else {
      store.get(reservation)
    }

  override def existsFor(crate: CrateStorageReservation.Id, node: Node.Id): Future[Boolean] =
    if (missingReservations.map(_.crate).contains(crate)) {
      Future.successful(false)
    } else {
      reservations.map(_.exists(r => r._2.crate == crate && r._2.target == node))
    }

  override def reservations: Future[Map[StoreKey, StoreValue]] =
    storeData.map { result =>
      (result.mapValues(value => Some(value)) ++ missingReservations.map(_.id -> None))
        .collect {
          case (k, Some(v)) => k -> v
        }
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] = store.entries
}
