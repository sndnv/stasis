package stasis.test.specs.unit.client.api.http.routes

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory

import stasis.client.api.Context
import stasis.client.api.http.routes.DatasetMetadata
import stasis.client.model
import stasis.client.ops.search.Search
import io.github.sndnv.layers.telemetry.mocks.MockAnalyticsCollector
import stasis.shared.model.datasets.DatasetEntry
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._

class DatasetMetadataSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.client.api.http.Formats._

  "DatasetMetadata routes" should "respond with dataset metadata for an entry" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(api = mockApiClient)

    val entry = DatasetEntry.generateId()

    Get(s"/$entry") ~> routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[model.DatasetMetadata]

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
    }
  }

  it should "respond with dataset metadata search results" in withRetry {
    val mockSearch = MockSearch()
    val routes = createRoutes(search = mockSearch)

    Get("/search?query=test.*") ~> routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Search.Result]

      mockSearch.statistics(MockSearch.Statistic.SearchExecuted) should be(1)
    }
  }

  def createRoutes(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    executor: MockOperationExecutor = MockOperationExecutor(),
    scheduler: MockOperationScheduler = MockOperationScheduler(),
    search: MockSearch = MockSearch()
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      trackers = MockTrackerViews(),
      search = search,
      handlers = Context.Handlers(
        terminateService = () => (),
        verifyUserPassword = _ => false,
        updateUserCredentials = (_, _) => Future.successful(Done),
        reEncryptDeviceSecret = _ => Future.successful(Done)
      ),
      commandProcessor = MockCommandProcessor(),
      secretsConfig = Fixtures.Secrets.DefaultConfig,
      analytics = new MockAnalyticsCollector,
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new DatasetMetadata().routes()
  }
}
