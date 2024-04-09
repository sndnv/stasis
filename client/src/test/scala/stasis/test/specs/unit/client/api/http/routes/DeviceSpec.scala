package stasis.test.specs.unit.client.api.http.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import stasis.client.api.http.Context
import stasis.client.api.http.routes.Device
import stasis.client.tracking.ServerTracker
import stasis.shared.model
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import java.time.Instant
import scala.concurrent.Future

class DeviceSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats._
  import stasis.shared.api.Formats._

  "Device routes" should "respond with the current device" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(api = mockApiClient)

    Get("/") ~> routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[model.devices.Device]

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
    }
  }

  they should "respond with the current state of the device connections" in withRetry {
    val expectedServers = Map(
      "server-01" -> ServerTracker.ServerState(reachable = true, timestamp = Instant.now()),
      "server-02" -> ServerTracker.ServerState(reachable = false, timestamp = Instant.now())
    )

    val mockTrackers = new MockTrackerViews() {
      override val server: ServerTracker.View = new MockServerTracker() {
        override def state: Future[Map[String, ServerTracker.ServerState]] =
          Future.successful(expectedServers)
      }
    }

    val routes = createRoutes(trackers = mockTrackers)

    Get("/connections") ~> routes ~> check {
      status should be(StatusCodes.OK)
      val actualServers = responseAs[Map[String, ServerTracker.ServerState]]

      actualServers should be(expectedServers)
    }
  }

  def createRoutes(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    executor: MockOperationExecutor = MockOperationExecutor(),
    scheduler: MockOperationScheduler = MockOperationScheduler(),
    trackers: MockTrackerViews = MockTrackerViews()
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      trackers = trackers,
      search = MockSearch(),
      terminateService = () => (),
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new Device().routes()
  }
}
