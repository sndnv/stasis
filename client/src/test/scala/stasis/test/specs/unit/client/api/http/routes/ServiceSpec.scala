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
import stasis.layers.telemetry.ApplicationInformation
import stasis.layers.telemetry.analytics.AnalyticsCollector
import stasis.layers.telemetry.analytics.AnalyticsEntry
import stasis.layers.telemetry.analytics.MockAnalyticsCollector
import stasis.layers.telemetry.analytics.MockAnalyticsPersistence
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

  they should "provide analytics state" in withRetry {
    val routes = createRoutes()

    Get("/analytics") ~> routes ~> check {
      status should be(StatusCodes.OK)
      val state = responseAs[Service.AnalyticsState]

      state.entry.runtime should be(AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none))
      state.entry.events should be(empty)
      state.entry.failures should be(empty)
      state.lastCached should not be empty
      state.lastTransmitted should not be empty
    }
  }

  they should "support sending analytics state remotely" in withRetry {
    val collector = new MockAnalyticsCollector

    val persistence = collector.persistence match {
      case Some(persistence: MockAnalyticsPersistence) =>
        persistence

      case other =>
        fail(s"Unexpected analytics persistence object provided: [$other]")
    }

    val routes = createRoutes(analytics = collector)

    persistence.transmitted should be(empty)

    Put("/analytics/send") ~> routes ~> check {
      status should be(StatusCodes.Accepted)

      eventually {
        persistence.transmitted should not be empty
      }
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

  def createRoutes(terminate: () => Unit = () => (), analytics: AnalyticsCollector = new MockAnalyticsCollector): Route = {
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
      analytics = analytics,
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new Service().routes()
  }
}
