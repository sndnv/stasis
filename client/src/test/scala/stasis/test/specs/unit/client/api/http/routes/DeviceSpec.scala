package stasis.test.specs.unit.client.api.http.routes

import java.time.Instant

import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.client.api.http.Context
import stasis.client.api.http.routes.Device
import stasis.client.tracking.TrackerView
import stasis.shared.model
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.concurrent.Future

class DeviceSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats._
  import stasis.shared.api.Formats._

  "Device routes" should "respond with the current device" in {
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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
    }
  }

  they should "respond with the current state of the device connections" in {
    val expectedServers = Map(
      "server-01" -> TrackerView.ServerState(reachable = true, timestamp = Instant.now()),
      "server-02" -> TrackerView.ServerState(reachable = false, timestamp = Instant.now())
    )

    val mockTracker = new MockTrackerView() {
      override def state: Future[TrackerView.State] = Future.successful(
        TrackerView.State(
          operations = Map.empty,
          servers = expectedServers
        )
      )
    }
    val routes = createRoutes(tracker = mockTracker)

    Get("/connections") ~> routes ~> check {
      status should be(StatusCodes.OK)
      val actualServers = responseAs[Map[String, TrackerView.ServerState]]

      actualServers should be(expectedServers)
    }
  }

  def createRoutes(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    executor: MockOperationExecutor = MockOperationExecutor(),
    scheduler: MockOperationScheduler = MockOperationScheduler(),
    tracker: MockTrackerView = MockTrackerView()
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      tracker = tracker,
      search = MockSearch(),
      log = Logging(system, this.getClass.getName)
    )

    new Device().routes()
  }
}
