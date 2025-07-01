package stasis.test.specs.unit.client.api.http

import scala.collection.mutable
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory

import stasis.client.api.Context
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.api.http.HttpApiEndpoint
import stasis.client.model.DatasetMetadata
import io.github.sndnv.layers.api.MessageResponse
import io.github.sndnv.layers.telemetry.mocks.MockAnalyticsCollector
import stasis.shared.api.responses.Ping
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.Fixtures
import stasis.test.specs.unit.client.mocks._
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class HttpApiEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.client.api.http.Formats._
  import stasis.shared.api.Formats._

  "An HttpApiEndpoint" should "successfully authenticate users" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/user")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[User]
    }
  }

  it should "fail to authenticate users with no credentials" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/user") ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with invalid credentials" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/user")
      .addCredentials(testCredentials.copy(password = "invalid")) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide routes for service management" in withRetry {
    val endpoint = createEndpoint()

    Get("/service/ping")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  it should "provide routes for dataset definitions" in withRetry {
    val endpoint = createEndpoint()

    Get("/datasets/definitions")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should not be empty
    }
  }

  it should "provide routes for dataset entries" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/datasets/entries/for-definition/${DatasetDefinition.generateId()}")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should not be empty
    }
  }

  it should "provide routes for dataset metadata" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/datasets/metadata/${DatasetEntry.generateId()}")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[DatasetMetadata]
    }
  }

  it should "provide routes for users" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/user")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[User]
    }
  }

  it should "provide routes for devices" in withRetry {
    val endpoint = createEndpoint()

    Get(s"/device")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Device]
    }
  }

  it should "provide routes for schedules" in withRetry {
    val endpoint = createEndpoint()

    Get("/schedules/public")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should not be empty
    }
  }

  it should "provide routes for operations" in withRetry {
    val endpoint = createEndpoint()

    Put(s"/operations/${Operation.generateId()}/stop")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.NoContent)
    }
  }

  it should "handle server API failures reported by routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val expectedStatus = StatusCodes.Forbidden
    val expectedMessage = "test failure"

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def user(): Future[User] =
        Future.failed(
          new ServerApiFailure(status = expectedStatus, message = expectedMessage)
        )
    }

    val endpoint = createEndpoint(api = mockApiClient)

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/user"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(expectedStatus)
        Unmarshal(response).to[MessageResponse].await.message should be(expectedMessage)
      }
  }

  it should "handle generic failures reported by routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val mockApiClient = new MockServerApiEndpointClient(self = Device.generateId()) {
      override def user(): Future[User] = Future.failed(new RuntimeException("test failure"))
    }

    val endpoint = createEndpoint(api = mockApiClient)

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/user"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response).to[MessageResponse].await.message should startWith("Unhandled exception encountered")
      }
  }

  private def createEndpoint(
    api: MockServerApiEndpointClient = MockServerApiEndpointClient()
  ): HttpApiEndpoint = {
    implicit val context: Context = Context(
      api = api,
      executor = MockOperationExecutor(),
      scheduler = MockOperationScheduler(),
      trackers = MockTrackerViews(),
      search = MockSearch(),
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

    new HttpApiEndpoint(
      authenticator = {
        case BasicHttpCredentials(`testUser`, `testPassword`) =>
          Future.successful(Done)

        case other =>
          Future.failed(new RuntimeException(s"Unexpected credentials provided: [$other]"))
      }
    )
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "HttpApiEndpointSpec"
  )

  private implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser, password = testPassword)

  private val ports: mutable.Queue[Int] = (28000 to 28100).to(mutable.Queue)
}
