package stasis.test.specs.unit.client.api.http.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import stasis.client.api.http.Context
import stasis.client.api.http.routes.User
import stasis.shared.model
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

class UserSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  "User routes" should "respond with the current user" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(api = mockApiClient)

    Get("/") ~> routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[model.users.User]

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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
    }
  }

  def createRoutes(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    executor: MockOperationExecutor = MockOperationExecutor(),
    scheduler: MockOperationScheduler = MockOperationScheduler()
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      trackers = MockTrackerViews(),
      search = MockSearch(),
      terminateService = () => (),
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new User().routes()
  }
}
