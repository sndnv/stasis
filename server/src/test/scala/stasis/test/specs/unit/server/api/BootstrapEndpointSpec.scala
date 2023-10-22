package stasis.test.specs.unit.server.api

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials, OAuth2BearerToken}
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import play.api.libs.json.Json
import stasis.core.api.MessageResponse
import stasis.core.routing.Node
import stasis.core.telemetry.TelemetryContext
import stasis.server.api.BootstrapEndpoint
import stasis.server.api.routes.DeviceBootstrap
import stasis.server.model.devices.{DeviceBootstrapCodeStore, DeviceStore}
import stasis.server.model.users.UserStore
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.{BootstrapCodeAuthenticator, UserAuthenticator}
import stasis.shared.model.devices.{Device, DeviceBootstrapCode, DeviceBootstrapParameters}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.Secrets
import stasis.test.specs.unit.server.model.mocks._
import stasis.test.specs.unit.server.security.mocks._

import java.time.Instant
import scala.collection.mutable

class BootstrapEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  "A BootstrapEndpoint" should "successfully authenticate requests with user credentials" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.manageSelf().put(Seq(device.id), bootstrapCode).await

    Get("/devices/codes").addCredentials(testUserCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceBootstrapCode]] should be(Seq(bootstrapCode.copy(value = "*****")))
    }
  }

  it should "successfully authenticate requests with bootstrap codes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(device).await
    fixtures.userStore.manage().create(user).await

    Put("/devices/execute").addCredentials(testCodeCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "fail to authenticate requests with no credentials" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/devices/codes") ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate requests with invalid credentials" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/devices/codes")
      .addCredentials(testUserCredentials.copy(username = "invalid-username")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide bootstrap code management routes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.manageSelf().put(Seq(device.id), bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Delete(s"/devices/codes/for-device/${bootstrapCode.device}")
      .addCredentials(testUserCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  it should "provide device bootstrap execution routes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(device).await
    fixtures.userStore.manage().create(user).await

    Put("/devices/execute").addCredentials(testCodeCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "handle authorization failures reported by routes" in withRetry {
    val fixtures = new TestFixtures {
      override lazy val provider: ResourceProvider = new MockResourceProvider(resources = Set.empty)
    }

    val endpointPort = ports.dequeue()
    val _ = fixtures.endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/devices/codes"
        ).addCredentials(testUserCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Forbidden)
      }
  }

  it should "handle generic failures reported by routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

    val fixtures = new TestFixtures {
      override lazy val userAuthenticator: UserAuthenticator =
        (_: HttpCredentials) => throw new RuntimeException("test failure")
    }

    val endpointPort = ports.dequeue()
    val _ = fixtures.endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.DELETE,
          uri = s"http://localhost:$endpointPort/devices/codes/for-device/${device.id}"
        ).addCredentials(testUserCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "BootstrapEndpointSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val userStore: UserStore = MockUserStore()
    lazy val deviceStore: DeviceStore = MockDeviceStore()
    lazy val bootstrapCodeStore: DeviceBootstrapCodeStore = MockDeviceBootstrapCodeStore()
    lazy val bootstrapContext: DeviceBootstrap.BootstrapContext = DeviceBootstrap.BootstrapContext(
      bootstrapCodeGenerator = new MockDeviceBootstrapCodeGenerator(),
      clientSecretGenerator = new MockDeviceClientSecretGenerator(),
      credentialsManager = new MockDeviceCredentialsManager(),
      deviceParams = baseParams
    )

    lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        deviceStore.manage(),
        deviceStore.manageSelf(),
        deviceStore.view(),
        deviceStore.viewSelf(),
        userStore.manage(),
        userStore.manageSelf(),
        userStore.view(),
        userStore.viewSelf(),
        bootstrapCodeStore.manage(),
        bootstrapCodeStore.manageSelf(),
        bootstrapCodeStore.view(),
        bootstrapCodeStore.viewSelf()
      )
    )

    lazy val userAuthenticator: UserAuthenticator = new MockUserAuthenticator(user.id.toString, userPassword)
    lazy val codeAuthenticator: BootstrapCodeAuthenticator = new MockBootstrapCodeAuthenticator(bootstrapCode)

    lazy val endpoint: BootstrapEndpoint = new BootstrapEndpoint(
      resourceProvider = provider,
      userAuthenticator = userAuthenticator,
      bootstrapCodeAuthenticator = codeAuthenticator,
      deviceBootstrapContext = bootstrapContext
    )
  }

  private val baseParams = DeviceBootstrapParameters(
    authentication = DeviceBootstrapParameters.Authentication(
      tokenEndpoint = "http://localhost:1234",
      clientId = "",
      clientSecret = "",
      useQueryString = true,
      scopes = DeviceBootstrapParameters.Scopes(
        api = "urn:stasis:identity:audience:server-api",
        core = s"urn:stasis:identity:audience:${Node.generateId().toString}"
      ),
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = "",
      userSalt = "",
      device = "",
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = "",
      context = DeviceBootstrapParameters.Context.disabled()
    ),
    secrets = testSecretsConfig,
    additionalConfig = Json.obj()
  )

  private val user = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty
  )

  private val device = Device(
    id = Device.generateId(),
    name = "test-device",
    node = Node.generateId(),
    owner = user.id,
    active = true,
    limits = None
  )

  private val userPassword = "test-password"

  private val bootstrapCode: DeviceBootstrapCode = DeviceBootstrapCode(
    value = "test-code",
    owner = user.id,
    device = device.id,
    expiresAt = Instant.now().plusSeconds(42)
  )

  private val testUserCredentials = BasicHttpCredentials(username = user.id.toString, password = userPassword)
  private val testCodeCredentials = OAuth2BearerToken(token = bootstrapCode.value)

  private val ports: mutable.Queue[Int] = (32000 to 32100).to(mutable.Queue)
}
