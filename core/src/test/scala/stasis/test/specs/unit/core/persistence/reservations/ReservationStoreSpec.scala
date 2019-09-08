package stasis.test.specs.unit.core.persistence.reservations

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.packaging.Crate
import stasis.core.persistence.{CrateStorageReservation, StoreInitializationResult}
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class ReservationStoreSpec extends AsyncUnitSpec {
  "A ReservationStore" should "add, retrieve and delete reservations" in {
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

  it should "expire old reservations" in {
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
      _ <- akka.pattern.after(expiration * 2, using = system.scheduler)(Future.successful(Done))
      missingReservation <- store.get(expectedReservation.id)
    } yield {
      actualReservation should be(Some(expectedReservation))
      missingReservation should be(None)
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

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "ReservationStoreSpec"
  )

  private def createStore(reservationExpiration: FiniteDuration = 3.seconds): ReservationStore = {
    val StoreInitializationResult(store, init) = ReservationStore(
      expiration = reservationExpiration,
      backend = MemoryBackend[CrateStorageReservation.Id, CrateStorageReservation](
        name = s"reservation-store-${java.util.UUID.randomUUID()}"
      )
    )

    val _ = init.await

    store
  }
}
