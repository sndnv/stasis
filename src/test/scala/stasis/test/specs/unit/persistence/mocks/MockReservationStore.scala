package stasis.test.specs.unit.persistence.mocks

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.persistence.CrateStorageReservation
import stasis.persistence.CrateStorageReservation.Id
import stasis.persistence.reservations.ReservationStore

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class MockReservationStore(
  missingReservations: Seq[CrateStorageReservation] = Seq.empty,
  ignoreMissingReservations: Boolean = true
)(implicit system: ActorSystem[SpawnProtocol])
    extends ReservationStore {
  private type StoreKey = CrateStorageReservation.Id
  private type StoreValue = CrateStorageReservation

  private implicit val timeout: Timeout = 3.seconds
  private implicit val scheduler: Scheduler = system.scheduler
  private implicit val ec: ExecutionContext = system.executionContext

  private val storeRef =
    system ? SpawnProtocol.Spawn(
      MapStoreActor.store(Map.empty[StoreKey, StoreValue]),
      s"mock-reservation-store-${java.util.UUID.randomUUID()}"
    )

  override def put(reservation: StoreValue): Future[Done] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.Put(reservation.id, reservation, ref)))

  override def discard(crate: Id): Future[Boolean] =
    reservations().flatMap { reservations =>
      reservations.find(_.crate == crate) match {
        case Some(reservation) =>
          val result: Future[Done] = storeRef.flatMap(_ ? (ref => MapStoreActor.Remove(reservation.id, ref)))
          result.map(_ => true)

        case None =>
          if (ignoreMissingReservations) {
            Future.successful(true)
          } else {
            Future.successful(false)
          }
      }
    }

  override def get(reservation: Id): Future[Option[StoreValue]] =
    if (missingReservations.map(_.id).contains(reservation)) {
      Future.successful(None)
    } else {
      storeRef.flatMap(_ ? (ref => MapStoreActor.Get(reservation, ref)))
    }

  override def existsFor(crate: Id): Future[Boolean] =
    if (missingReservations.map(_.crate).contains(crate)) {
      Future.successful(false)
    } else {
      reservations().map(_.exists(_.crate == crate))
    }

  override def reservations(): Future[Seq[StoreValue]] =
    storeData.map { result =>
      (result.mapValues(value => Some(value)) ++ missingReservations.map(_.id -> None)).values.flatten.toSeq
    }

  private def storeData: Future[Map[StoreKey, StoreValue]] =
    storeRef.flatMap(_ ? (ref => MapStoreActor.GetAll(ref)))
}
