package stasis.test.specs.unit.core.persistence.reservations

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class MockReservationStore(
  missingReservations: Seq[CrateStorageReservation],
  ignoreMissingReservations: Boolean,
  underlying: KeyValueStore[CrateStorageReservation.Id, CrateStorageReservation]
)(implicit system: ActorSystem[Nothing])
    extends ReservationStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()

  override def put(reservation: CrateStorageReservation): Future[Done] =
    underlying.put(reservation.id, reservation)

  override def delete(crate: CrateStorageReservation.Id, node: Node.Id): Future[Boolean] =
    reservations.flatMap { reservations =>
      reservations.find(r => r.crate == crate && r.target == node) match {
        case Some(reservation) =>
          underlying.delete(reservation.id)

        case None =>
          if (ignoreMissingReservations) {
            Future.successful(true)
          } else {
            Future.successful(false)
          }
      }
    }

  override def get(reservation: CrateStorageReservation.Id): Future[Option[CrateStorageReservation]] =
    if (missingReservations.map(_.id).contains(reservation)) {
      Future.successful(None)
    } else {
      underlying.get(reservation)
    }

  override def existsFor(crate: CrateStorageReservation.Id, node: Node.Id): Future[Boolean] =
    if (missingReservations.map(_.crate).contains(crate)) {
      Future.successful(false)
    } else {
      reservations.map(_.exists(r => r.crate == crate && r.target == node))
    }

  override def reservations: Future[Seq[CrateStorageReservation]] =
    underlying.entries.map { result =>
      (result.view.mapValues(value => Some(value)) ++ missingReservations.map(_.id -> None)).collect { case (_, Some(v)) =>
        v
      }.toSeq
    }
}

object MockReservationStore {
  def apply(
    missingReservations: Seq[CrateStorageReservation] = Seq.empty,
    ignoreMissingReservations: Boolean = true
  )(implicit system: ActorSystem[Nothing]): MockReservationStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    implicit val timeout: Timeout = 3.seconds

    val underlying = MemoryStore[CrateStorageReservation.Id, CrateStorageReservation](
      name = s"mock-reservation-store-${java.util.UUID.randomUUID()}"
    )

    new MockReservationStore(
      missingReservations = missingReservations,
      ignoreMissingReservations = ignoreMissingReservations,
      underlying = underlying
    )
  }
}
