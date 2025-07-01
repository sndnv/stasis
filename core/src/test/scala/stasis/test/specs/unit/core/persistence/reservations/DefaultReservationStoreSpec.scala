package stasis.test.specs.unit.core.persistence.reservations

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.packaging.Crate
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.DefaultReservationStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.routing.Node
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class DefaultReservationStoreSpec extends AsyncUnitSpec {
  "A DefaultReservationStore" should "add, retrieve and delete reservations" in {
    val store = createStore()

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
      _ <- store.init()
      _ <- store.put(expectedReservation)
      actualReservation <- store.get(expectedReservation.id)
      someReservations <- store.reservations
      _ <- store.delete(expectedReservation.crate, expectedReservation.target)
      missingReservation <- store.get(expectedReservation.id)
      noReservations <- store.reservations
      _ <- store.drop()
    } yield {
      actualReservation should be(Some(expectedReservation))
      someReservations should be(Seq(expectedReservation))
      missingReservation should be(None)
      noReservations should be(Seq.empty)
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
      target = Node.generateId(),
      created = Instant.now()
    )

    for {
      _ <- store.init()
      _ <- store.put(expectedReservation)
      reservationExists <- store.existsFor(expectedReservation.crate, expectedReservation.target)
      reservationMissingTarget <- store.existsFor(expectedReservation.crate, Node.generateId())
      reservationMissingCrate <- store.existsFor(Crate.generateId(), expectedReservation.target)
      _ <- store.drop()
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
      target = Node.generateId(),
      created = Instant.now()
    )

    for {
      _ <- store.init()
      _ <- store.put(expectedReservation)
      actualReservation <- store.get(expectedReservation.id)
      _ <- after(expiration * 2)(Future.successful(Done))
      missingReservation <- store.get(expectedReservation.id)
      _ <- store.drop()
    } yield {
      actualReservation should be(Some(expectedReservation))
      missingReservation should be(None)
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultReservationStoreSpec"
  )

  private def createStore(
    reservationExpiration: FiniteDuration = 3.seconds
  ): ReservationStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    DefaultReservationStore(
      name = "test-reservation-store",
      expiration = reservationExpiration
    )
  }

}
