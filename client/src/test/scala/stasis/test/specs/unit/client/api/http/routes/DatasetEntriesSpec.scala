package stasis.test.specs.unit.client.api.http.routes

import java.time.Instant

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory
import stasis.client.api.Context
import stasis.client.api.http.routes.DatasetEntries
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._
import scala.concurrent.Future

import org.apache.pekko.Done
import stasis.test.specs.unit.client.Fixtures

class DatasetEntriesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  "DatasetEntries routes" should "respond with all entries" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(api = mockApiClient)

    Get(s"/") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should not be empty

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(2)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
    }
  }

  they should "respond with all entries for a definition" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(api = mockApiClient)

    val definition = DatasetDefinition.generateId()

    Get(s"/$definition") ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should not be empty

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
    }
  }

  they should "respond with the latest entry for a definition" in withRetry {
    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(api = mockApiClient)

    val definition = DatasetDefinition.generateId()

    Get(s"/$definition/latest") ~> routes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[DatasetEntry]

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(1)
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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
    }
  }

  they should "fail to retrieve latest entry if none could be found" in withRetry {
    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def latestEntry(definition: DatasetDefinition.Id, until: Option[Instant]): Future[Option[DatasetEntry]] =
        Future.successful(None)
    }
    val routes = createRoutes(api = mockApiClient)

    val definition = DatasetDefinition.generateId()

    Get(s"/$definition/latest") ~> routes ~> check {
      status should be(StatusCodes.NotFound)

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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
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
      handlers = Context.Handlers(
        terminateService = () => (),
        verifyUserPassword = _ => false,
        updateUserCredentials = (_, _) => Future.successful(Done),
        reEncryptDeviceSecret = _ => Future.successful(Done)
      ),
      secretsConfig = Fixtures.Secrets.DefaultConfig,
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new DatasetEntries().routes()
  }
}
