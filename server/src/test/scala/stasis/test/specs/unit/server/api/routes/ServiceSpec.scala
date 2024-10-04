package stasis.test.specs.unit.server.api.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.server.api.routes.RoutesContext
import stasis.server.api.routes.Service
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
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
