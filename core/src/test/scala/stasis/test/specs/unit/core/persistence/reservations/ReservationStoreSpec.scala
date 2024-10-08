package stasis.test.specs.unit.core.persistence.reservations

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.packaging.Crate
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.StoreInitializationResult
import stasis.core.routing.Node
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ReservationStoreSpec extends AsyncUnitSpec {
  "A ReservationStore" should "add, retrieve and delete reservations" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = createStore()

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    for {
      _ <- store.put(expectedReservation)
      actualReservation <- store.get(expectedReservation.id)
      someReservations <- store.reservations
      _ <- store.delete(expectedReservation.crate, expectedReservation.target)
      missingReservation <- store.get(expectedReservation.id)
      noReservations <- store.reservations
    } yield {
      actualReservation should be(Some(expectedReservation))
      someReservations should be(Map(expectedReservation.id -> expectedReservation))
      missingReservation should be(None)
      noReservations should be(Map.empty)

      telemetry.core.persistence.reservation.reservation should be(1)
    }
  }

  it should "check for existing reservations" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = createStore()

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    for {
      _ <- store.put(expectedReservation)
      reservationExists <- store.existsFor(expectedReservation.crate, expectedReservation.target)
      reservationMissingTarget <- store.existsFor(expectedReservation.crate, Node.generateId())
      reservationMissingCrate <- store.existsFor(Crate.generateId(), expectedReservation.target)
    } yield {
      reservationExists should be(true)
      reservationMissingTarget should be(false)
      reservationMissingCrate should be(false)

      telemetry.core.persistence.reservation.reservation should be(1)
    }
  }

  it should "expire old reservations" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val expiration = 100.millis
    val store = createStore(reservationExpiration = expiration)

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    for {
      _ <- store.put(expectedReservation)
      actualReservation <- store.get(expectedReservation.id)
      _ <- after(expiration * 2, using = system)(Future.successful(Done))
      missingReservation <- store.get(expectedReservation.id)
    } yield {
      actualReservation should be(Some(expectedReservation))
      missingReservation should be(None)

      telemetry.core.persistence.reservation.reservation should be(1)
    }
  }

  it should "provide a read-only view" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = createStore()
    val storeView = store.view

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    for {
      _ <- store.put(expectedReservation)
      actualReservation <- storeView.get(expectedReservation.id)
      reservationExists <- storeView.existsFor(expectedReservation.crate, expectedReservation.target)
      someReservations <- storeView.reservations
      _ <- store.delete(expectedReservation.crate, expectedReservation.target)
      missingReservation <- storeView.get(expectedReservation.id)
      reservationMissing <- storeView.existsFor(expectedReservation.crate, expectedReservation.target)
      noReservations <- storeView.reservations
    } yield {
      actualReservation should be(Some(expectedReservation))
      someReservations should be(Map(expectedReservation.id -> expectedReservation))
      missingReservation should be(None)
      noReservations should be(Map.empty)
      reservationExists should be(true)
      reservationMissing should be(false)

      telemetry.core.persistence.reservation.reservation should be(1)

      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ReservationStore] }
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ReservationStoreSpec"
  )

  private def createStore(
    reservationExpiration: FiniteDuration = 3.seconds
  )(implicit telemetry: TelemetryContext): ReservationStore = {
    val StoreInitializationResult(store, init) = ReservationStore(
      expiration = reservationExpiration,
      backend = MemoryStore[CrateStorageReservation.Id, CrateStorageReservation](
        name = s"reservation-store-${java.util.UUID.randomUUID()}"
      )
    )

    val _ = init().await

    store
  }
}
