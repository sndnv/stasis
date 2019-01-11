package stasis.test.specs.unit.persistence.reservations

import scala.concurrent.duration._

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.packaging.Crate
import stasis.persistence.CrateStorageReservation
import stasis.persistence.backends.memory.MemoryBackend
import stasis.persistence.reservations.ReservationStore
import stasis.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

class ReservationStoreSpec extends AsyncUnitSpec {

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "ReservationStoreSpec"
  )

  private def createStore(): ReservationStore = ReservationStore(
    backend = MemoryBackend[CrateStorageReservation.Id, CrateStorageReservation](
      name = s"reservation-store-${java.util.UUID.randomUUID()}"
    ),
    cache = MemoryBackend[(Crate.Id, Node.Id), CrateStorageReservation.Id](
      name = s"reservation-cache-${java.util.UUID.randomUUID()}"
    )
  )

  "A ReservationStore" should "add, retrieve and delete reservations" in {
    val store = createStore()

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      retention = 3.seconds,
      expiration = 1.second,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    for {
      _ <- store.put(expectedReservation)
      actualReservation <- store.get(expectedReservation.id)
      _ <- store.delete(expectedReservation.crate, expectedReservation.target)
      missingReservation <- store.get(expectedReservation.id)
    } yield {
      actualReservation should be(Some(expectedReservation))
      missingReservation should be(None)
    }
  }

  it should "check for existing reservations" in {
    val store = createStore()

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      retention = 3.seconds,
      expiration = 1.second,
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
    }
  }

  it should "provide a read-only view" in {
    val store = createStore()
    val storeView = store.view

    val expectedReservation = CrateStorageReservation(
      id = CrateStorageReservation.generateId(),
      crate = Crate.generateId(),
      size = 1,
      copies = 3,
      retention = 3.seconds,
      expiration = 1.second,
      origin = Node.generateId(),
      target = Node.generateId()
    )

    for {
      _ <- store.put(expectedReservation)
      actualReservation <- storeView.get(expectedReservation.id)
      reservationExists <- storeView.existsFor(expectedReservation.crate, expectedReservation.target)
      _ <- store.delete(expectedReservation.crate, expectedReservation.target)
      missingReservation <- storeView.get(expectedReservation.id)
      reservationMissing <- storeView.existsFor(expectedReservation.crate, expectedReservation.target)
    } yield {
      actualReservation should be(Some(expectedReservation))
      missingReservation should be(None)
      reservationExists should be(true)
      reservationMissing should be(false)
      a[ClassCastException] should be thrownBy { val _ = storeView.asInstanceOf[ReservationStore] }
    }
  }
}
