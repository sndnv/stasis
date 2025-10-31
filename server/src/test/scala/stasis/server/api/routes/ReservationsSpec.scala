package stasis.server.api.routes

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.server.events.mocks.MockEventCollector
import stasis.server.persistence.reservations.ServerReservationStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.reservations.MockReservationStore

class ReservationsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._

  "Reservations routes" should "respond with all reservations" in withRetry {
    val reservation = Generators.generateReservation
    reservationStore.put(reservation).await

    Get("/") ~> new Reservations().routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CrateStorageReservation]] should be(Seq(reservation))

      eventCollector.events should be(empty)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "ReservationsSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val reservationStore: ReservationStore = MockReservationStore()
  private val serverReservationStore: ServerReservationStore = ServerReservationStore(reservationStore)

  private implicit val provider: ResourceProvider = new MockResourceProvider(
    resources = Set(serverReservationStore.view())
  )

  lazy implicit val eventCollector: MockEventCollector = MockEventCollector()

  private implicit val context: RoutesContext = RoutesContext.collect()
  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
