package stasis.server.api

import java.time.Instant

import scala.collection.mutable

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods
import org.apache.pekko.http.scaladsl.model.HttpRequest
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import play.api.libs.json.Json

import stasis.core.routing.Node
import io.github.sndnv.layers.api.MessageResponse
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.server.Secrets
import stasis.server.api.routes.DeviceBootstrap
import stasis.server.persistence.devices.DeviceBootstrapCodeStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.devices.MockDeviceBootstrapCodeStore
import stasis.server.persistence.devices.MockDeviceStore
import stasis.server.persistence.nodes.ServerNodeStore
import stasis.server.persistence.users.MockUserStore
import stasis.server.persistence.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.BootstrapCodeAuthenticator
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.mocks._
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class BootstrapEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  "A BootstrapEndpoint" should "successfully authenticate requests with user credentials" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.manageSelf().put(self, bootstrapCode).await

    Get("/devices/codes").addCredentials(testUserCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceBootstrapCode]] should be(Seq(bootstrapCode.copy(value = "*****")))
    }
  }

  it should "successfully authenticate requests with bootstrap codes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().put(device).await
    fixtures.userStore.manage().put(user).await

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

    fixtures.bootstrapCodeStore.manageSelf().put(self, bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Delete(s"/devices/codes/${bootstrapCode.id}")
      .addCredentials(testUserCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  it should "provide device bootstrap execution routes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().put(device).await
    fixtures.userStore.manage().put(user).await

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
          uri = s"http://localhost:$endpointPort/v1/devices/codes"
        ).addCredentials(testUserCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Forbidden)
      }
  }

  it should "handle generic failures reported by routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

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
          uri = s"http://localhost:$endpointPort/v1/devices/codes/for-device/${device.id}"
        ).addCredentials(testUserCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "BootstrapEndpointSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val userStore: UserStore = MockUserStore()
    lazy val deviceStore: DeviceStore = MockDeviceStore()
    lazy val nodeStore: ServerNodeStore = ServerNodeStore(MockNodeStore())
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
        bootstrapCodeStore.viewSelf(),
        nodeStore.manageSelf()
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
      context = EndpointContext.Encoded.disabled()
    ),
    serverApi = DeviceBootstrapParameters.ServerApi(
      url = "http://localhost:5678",
      user = "",
      userSalt = "",
      device = "",
      context = EndpointContext.Encoded.disabled()
    ),
    serverCore = DeviceBootstrapParameters.ServerCore(
      address = "http://localhost:5679",
      nodeId = "",
      context = EndpointContext.Encoded.disabled()
    ),
    secrets = testSecretsConfig,
    additionalConfig = Json.obj()
  )

  private val user = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty,
    created = Instant.now(),
    updated = Instant.now()
  )

  private val device = Device(
    id = Device.generateId(),
    name = "test-device",
    node = Node.generateId(),
    owner = user.id,
    active = true,
    limits = None,
    created = Instant.now(),
    updated = Instant.now()
  )

  private val userPassword = "test-password"

  private val self: CurrentUser = CurrentUser(id = user.id)

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
