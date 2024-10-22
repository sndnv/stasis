package stasis.server.persistence.reservations

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.reservations.MockReservationStore

class ServerReservationStoreSpec extends AsyncUnitSpec {
  "A ServerReservationStore" should "provide a view resource (service)" in {
    val store = ServerReservationStore(store = MockReservationStore())

    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return a list of reservations via view resource (service)" in {
    val underlying = MockReservationStore()
    val store = ServerReservationStore(store = underlying)

    val expectedReservation = Generators.generateReservation
    underlying.put(expectedReservation).await

    store.view().list().map { result =>
      result.toList match {
        case actualReservation :: Nil => actualReservation should be(expectedReservation)
        case other                    => fail(s"Unexpected response received: [$other]")
      }
    }
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ServerReservationStoreSpec"
  )
}
