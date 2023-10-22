package stasis.test.specs.unit.server.model.reservations

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.reservations.ServerReservationStore
import stasis.shared.security.Permission
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ServerReservationStoreSpec extends AsyncUnitSpec {
  "A ServerReservationStore" should "provide a view resource (service)" in {
    val store = ServerReservationStore(store = new MockReservationStore())

    store.view().requiredPermission should be(Permission.View.Service)
  }

  it should "return a list of reservations via view resource (service)" in {
    val underlying = new MockReservationStore()
    val store = ServerReservationStore(store = underlying)

    val expectedReservation = Generators.generateReservation
    underlying.put(expectedReservation).await

    store.view().list().map { result =>
      result.values.toList match {
        case actualReservation :: Nil => actualReservation should be(expectedReservation)
        case other                    => fail(s"Unexpected response received: [$other]")
      }
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServerReservationStoreSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()
}
