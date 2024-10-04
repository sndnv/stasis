package stasis.test.specs.unit.server.api.routes

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.api.routes.Reservations
import stasis.server.api.routes.RoutesContext
import stasis.server.model.reservations.ServerReservationStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class ReservationsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._

  "Reservations routes" should "respond with all reservations" in withRetry {

    val reservation = Generators.generateReservation
    reservationStore.put(reservation).await

    Get("/") ~> new Reservations().routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CrateStorageReservation]] should be(Seq(reservation))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "ReservationsSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private val reservationStore: ReservationStore = new MockReservationStore()
  private val serverReservationStore: ServerReservationStore = ServerReservationStore(reservationStore)

  private implicit val provider: ResourceProvider = new MockResourceProvider(
    resources = Set(serverReservationStore.view())
  )

  private implicit val context: RoutesContext = RoutesContext.collect()
  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
