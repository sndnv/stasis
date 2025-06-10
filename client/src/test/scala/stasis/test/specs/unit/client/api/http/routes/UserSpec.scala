package stasis.test.specs.unit.client.api.http.routes

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory

import stasis.client.api.Context
import stasis.client.api.http.routes.User
import stasis.layers.telemetry.analytics.MockAnalyticsCollector
import stasis.shared.api.requests.UpdateUserPasswordOwn
import stasis.shared.api.requests.UpdateUserSaltOwn
import stasis.shared.model
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(1)
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

  they should "update the current user's password" in withRetry {
    val updatedUserCredentials = new AtomicBoolean(false)

    val currentPassword = "test-password"

    val mockApiClient = MockServerApiEndpointClient()

    val routes = createRoutes(
      api = mockApiClient,
      verifyUserPassword = provided => provided.sameElements(currentPassword.toCharArray),
      updateUserCredentials = (_, _) => {
        updatedUserCredentials.set(true)
        Future.successful(Done)
      }
    )

    val updateRequest = UpdateUserPasswordOwn(currentPassword = currentPassword, newPassword = "updated-password")

    Put("/password").withEntity(Marshal(updateRequest).to[RequestEntity].await) ~> routes ~> check {
      status should be(StatusCodes.OK)

      updatedUserCredentials.get() should be(true)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserSaltReset) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserPasswordUpdated) should be(1)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPushed) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyPulled) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DeviceKeyExists) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Ping) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
    }
  }

  they should "fail to update the user's password if the current password is not valid" in withRetry {
    val updatedSaltRef = new AtomicReference[Option[String]](None)
    val updatedUserCredentials = new AtomicBoolean(false)

    val currentPassword = "test-password"

    val mockApiClient = MockServerApiEndpointClient()

    val routes = createRoutes(
      api = mockApiClient,
      verifyUserPassword = provided => provided.sameElements(currentPassword.toCharArray),
      updateUserCredentials = (_, updatedSalt) => {
        updatedSaltRef.set(Some(updatedSalt))
        updatedUserCredentials.set(true)
        Future.successful(Done)
      }
    )

    val updateRequest = UpdateUserPasswordOwn(currentPassword = "other-password", newPassword = "updated-password")

    Put("/password").withEntity(Marshal(updateRequest).to[RequestEntity].await) ~> routes ~> check {
      status should be(StatusCodes.Conflict)

      updatedSaltRef.get() should be(None)
      updatedUserCredentials.get() should be(false)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
    }
  }

  they should "update the current user's salt" in withRetry {
    val updatedSaltRef = new AtomicReference[Option[String]](None)
    val updatedUserCredentials = new AtomicBoolean(false)

    val currentPassword = "test-password"

    val mockApiClient = MockServerApiEndpointClient()
    val routes = createRoutes(
      api = mockApiClient,
      verifyUserPassword = provided => provided.sameElements(currentPassword.toCharArray),
      updateUserCredentials = (_, updatedSalt) => {
        updatedSaltRef.set(Some(updatedSalt))
        updatedUserCredentials.set(true)
        Future.successful(Done)
      }
    )

    val updateRequest = UpdateUserSaltOwn(currentPassword = currentPassword, newSalt = "updated-salt")

    Put("/salt").withEntity(Marshal(updateRequest).to[RequestEntity].await) ~> routes ~> check {
      status should be(StatusCodes.OK)

      updatedSaltRef.get() should be(Some("updated-salt"))
      updatedUserCredentials.get() should be(true)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionsRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicSchedulesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.PublicScheduleRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryIdRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetMetadataWithEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.UserRetrieved) should be(1)
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

  they should "fail to update the user's salt if the current password is not valid" in withRetry {
    val updatedSaltRef = new AtomicReference[Option[String]](None)
    val updatedUserCredentials = new AtomicBoolean(false)

    val currentPassword = "test-password"

    val mockApiClient = MockServerApiEndpointClient()

    val routes = createRoutes(
      api = mockApiClient,
      verifyUserPassword = provided => provided.sameElements(currentPassword.toCharArray),
      updateUserCredentials = (_, updatedSalt) => {
        updatedSaltRef.set(Some(updatedSalt))
        updatedUserCredentials.set(true)
        Future.successful(Done)
      }
    )

    val updateRequest = UpdateUserSaltOwn(currentPassword = "other-password", newSalt = "updated-salt")

    Put("/salt").withEntity(Marshal(updateRequest).to[RequestEntity].await) ~> routes ~> check {
      status should be(StatusCodes.Conflict)

      updatedSaltRef.get() should be(None)
      updatedUserCredentials.get() should be(false)

      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryDeleted) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryRetrievedLatest) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntryCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetEntriesRetrieved) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionCreated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionUpdated) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.DatasetDefinitionDeleted) should be(0)
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
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.AnalyticsEntriesSent) should be(0)
      mockApiClient.statistics(MockServerApiEndpointClient.Statistic.Commands) should be(0)
    }
  }

  def createRoutes(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient(),
    executor: MockOperationExecutor = MockOperationExecutor(),
    scheduler: MockOperationScheduler = MockOperationScheduler(),
    verifyUserPassword: Array[Char] => Boolean = _ => false,
    updateUserCredentials: (Array[Char], String) => Future[Done] = (_, _) => Future.successful(Done)
  ): Route = {
    implicit val context: Context = Context(
      api = api,
      executor = executor,
      scheduler = scheduler,
      trackers = MockTrackerViews(),
      search = MockSearch(),
      handlers = Context.Handlers(
        terminateService = () => (),
        verifyUserPassword = verifyUserPassword,
        updateUserCredentials = updateUserCredentials,
        reEncryptDeviceSecret = _ => Future.successful(Done)
      ),
      commandProcessor = MockCommandProcessor(),
      secretsConfig = Fixtures.Secrets.DefaultConfig,
      analytics = new MockAnalyticsCollector,
      log = LoggerFactory.getLogger(this.getClass.getName)
    )

    new User().routes()
  }
}
