package stasis.test.specs.unit.server.api.routes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.reservations.ReservationStore
import stasis.server.api.routes.{Reservations, RoutesContext}
import stasis.server.model.reservations.ServerReservationStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockReservationStore
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class ReservationsSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._

  "Reservations routes" should "respond with all reservations" in {

    val reservation = Generators.generateReservation
    reservationStore.put(reservation).await

    Get("/") ~> new Reservations().routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CrateStorageReservation]] should be(Seq(reservation))
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ReservationsSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.classicSystem
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private val reservationStore: ReservationStore = new MockReservationStore()
  private val serverReservationStore: ServerReservationStore = ServerReservationStore(reservationStore)

  private implicit val provider: ResourceProvider = new MockResourceProvider(
    resources = Set(serverReservationStore.view())
  )

  private implicit val context: RoutesContext = RoutesContext.collect()
  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
