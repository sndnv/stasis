package stasis.test.specs.unit.server.api.routes

import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.server.api.routes.{RoutesContext, Service}
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.responses.Ping
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class ServiceSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  "Service routes" should "provide ping responses" in {
    val fixtures = new TestFixtures {}

    Get("/ping") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  private implicit val log: LoggingAdapter = Logging(system, this.getClass.getName)

  private trait TestFixtures {
    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set.empty
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Service().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
