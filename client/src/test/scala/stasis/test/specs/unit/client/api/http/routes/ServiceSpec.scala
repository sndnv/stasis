package stasis.test.specs.unit.client.api.http.routes

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.slf4j.LoggerFactory

import stasis.client.api.Context
import stasis.client.api.http.routes.Service
import stasis.shared.api.responses.Ping
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class ServiceSpec extends AsyncUnitSpec with ScalatestRouteTest with Eventually {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "Service routes" should "provide ping responses" in withRetry {
    val routes = createRoutes()

    Get("/ping") ~> routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  they should "support stopping the service" in withRetry {
    val terminationCounter = new AtomicInteger(0)
    val routes = createRoutes(terminate = () => { val _ = terminationCounter.incrementAndGet() })

    Put("/stop") ~> routes ~> check {
      status should be(StatusCodes.NoContent)
      eventually[Assertion] {
        terminationCounter.get should be(1)
      }
    }
  }

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  def createRoutes(terminate: () => Unit = () => ()): Route = {
    implicit val context: Context = Context(
      api = MockServerApiEndpointClient(),
      executor = MockOperationExecutor(),
      scheduler = MockOperationScheduler(),
      trackers = MockTrackerViews(),
      search = MockSearch(),
      handlers = Context.Handlers(
        terminateService = terminate,
        verifyUserPassword = _ => false,
        updateUserCredentials = (_, _) => Future.successful(Done),
        reEncryptDeviceSecret = _ => Future.successful(Done)
      ),
      commandProcessor = MockCommandProcessor(),
      secretsConfig = Fixtures.Secrets.DefaultConfig,
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new Service().routes()
  }
}
