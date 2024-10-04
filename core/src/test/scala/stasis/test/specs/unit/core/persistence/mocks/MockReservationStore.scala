package stasis.test.specs.unit.core.persistence.mocks

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext

class MockReservationStore(
  missingReservations: Seq[CrateStorageReservation] = Seq.empty,
  ignoreMissingReservations: Boolean = true
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ReservationStore {
  private type StoreKey = CrateStorageReservation.Id
  private type StoreValue = CrateStorageReservation

  private implicit val timeout: Timeout = 3.seconds
  private implicit val ec: ExecutionContext = system.executionContext

  private val store = MemoryStore[StoreKey, StoreValue](name = s"mock-reservation-store-${java.util.UUID.randomUUID()}")

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
      (result.view.mapValues(value => Some(value)) ++ missingReservations.map(_.id -> None)).collect { case (k, Some(v)) =>
        k -> v
      }.toMap
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] = store.entries
}
