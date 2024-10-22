package stasis.test.specs.unit.core.persistence.reservations

import java.time.Instant

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class ReservationStoreSpec extends AsyncUnitSpec {
  "A ReservationStore" should "add, retrieve and delete reservations" in {
    val store = MockReservationStore()

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId(),
      created = Instant.now()
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
      someReservations should be(Seq(expectedReservation))
      missingReservation should be(None)
      noReservations should be(Seq.empty)
    }
  }

  it should "check for existing reservations" in {
    val store = MockReservationStore()

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId(),
      created = Instant.now()
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
    }
  }

  it should "provide a read-only view" in {
    val store = MockReservationStore()
    val storeView = store.view

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      origin = Node.generateId(),
      target = Node.generateId(),
      created = Instant.now()
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
      someReservations should be(Seq(expectedReservation))
      missingReservation should be(None)
      noReservations should be(Seq.empty)
      reservationExists should be(true)
      reservationMissing should be(false)

      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ReservationStore] }
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ReservationStoreSpec"
  )
}
