package stasis.test.specs.unit.client.api.http

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.slf4j.LoggerFactory
import stasis.client.api.clients.exceptions.ServerApiFailure
import stasis.client.api.http.{Context, HttpApiEndpoint}
import stasis.client.model.DatasetMetadata
import stasis.core.api.MessageResponse
import stasis.shared.api.responses.Ping
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.shared.ops.Operation
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.client.mocks._

import scala.collection.mutable
import scala.concurrent.Future

class HttpApiEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.client.api.http.Formats._
  import stasis.shared.api.Formats._

  "An HttpApiEndpoint" should "successfully authenticate users" in {
    val endpoint = createEndpoint()

    Get(s"/user")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[User]
    }
  }

  it should "fail to authenticate users with no credentials" in {
    val endpoint = createEndpoint()

    Get(s"/user") ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with invalid credentials" in {
    val endpoint = createEndpoint()

    Get(s"/user")
      .addCredentials(testCredentials.copy(password = "invalid")) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide routes for service management" in {
    val endpoint = createEndpoint()

    Get("/service/ping")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  it should "provide routes for dataset definitions" in {
    val endpoint = createEndpoint()

    Get("/datasets/definitions")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should not be empty
    }
  }

  it should "provide routes for dataset entries" in {
    val endpoint = createEndpoint()

    Get(s"/datasets/entries/${DatasetDefinition.generateId()}")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should not be empty
    }
  }

  it should "provide routes for dataset metadata" in {
    val endpoint = createEndpoint()

    Get(s"/datasets/metadata/${DatasetEntry.generateId()}")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[DatasetMetadata]
    }
  }

  it should "provide routes for users" in {
    val endpoint = createEndpoint()

    Get(s"/user")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[User]
    }
  }

  it should "provide routes for devices" in {
    val endpoint = createEndpoint()

    Get(s"/device")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Device]
    }
  }

  it should "provide routes for schedules" in {
    val endpoint = createEndpoint()

    Get("/schedules/public")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should not be empty
    }
  }

  it should "provide routes for operations" in {
    val endpoint = createEndpoint()

    Put(s"/operations/${Operation.generateId()}/stop")
      .addCredentials(testCredentials) ~> endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.NoContent)
    }
  }

  it should "handle server API failures reported by routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

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

  it should "handle generic failures reported by routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

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
      tracker = MockTrackerView(),
      search = MockSearch(),
      terminateService = () => (),
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

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "HttpApiEndpointSpec"
  )

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser, password = testPassword)

  private val ports: mutable.Queue[Int] = (28000 to 28100).to(mutable.Queue)
}
