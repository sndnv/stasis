package stasis.test.specs.unit.server.api.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import stasis.server.api.routes.{RoutesContext, Service}
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.responses.Ping
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class ServiceSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  "Service routes" should "provide ping responses" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/ping") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  it should "provide a health-check route" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/health") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set.empty
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Service().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())
}
