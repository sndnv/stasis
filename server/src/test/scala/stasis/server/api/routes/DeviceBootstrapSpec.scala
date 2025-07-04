package stasis.server.api.routes

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

import stasis.core.routing.Node
import io.github.sndnv.layers.security.tls.EndpointContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.server.Secrets
import stasis.server.persistence.devices.DeviceBootstrapCodeStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.devices.MockDeviceBootstrapCodeStore
import stasis.server.persistence.devices.MockDeviceStore
import stasis.server.persistence.nodes.ServerNodeStore
import stasis.server.persistence.users.MockUserStore
import stasis.server.persistence.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks._
import stasis.shared.api.requests.CreateDeviceOwn
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceBootstrapParameters
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.shared.model.Generators

class DeviceBootstrapSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  "DeviceBootstrap routes (full permissions)" should "respond with all device bootstrap codes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    val codes = Seq(
      Generators.generateDeviceBootstrapCode,
      Generators.generateDeviceBootstrapCode,
      Generators.generateDeviceBootstrapCode
    )

    Future.sequence(codes.map(fixtures.bootstrapCodeStore.manage().put)).await

    Get("/devices/codes") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceBootstrapCode]] should contain theSameElementsAs codes.map(_.copy(value = "*****"))
    }
  }

  they should "delete existing device bootstrap codes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Delete(s"/devices/codes/${bootstrapCode.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  they should "not delete missing device bootstrap codes" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)

    Delete(s"/devices/codes/${bootstrapCode.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  "DeviceBootstrap routes (self permissions)" should "respond with all device bootstrap codes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    val codes = Seq(
      Generators.generateDeviceBootstrapCode.copy(owner = currentUser.id, target = Left(devices.head.id)),
      Generators.generateDeviceBootstrapCode.copy(owner = currentUser.id, target = Left(devices.head.id)),
      Generators.generateDeviceBootstrapCode
    )

    Future.sequence(codes.map(fixtures.bootstrapCodeStore.manage().put)).await
    Future.sequence(devices.map(fixtures.deviceStore.manage().put)).await

    Get("/devices/codes/own") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceBootstrapCode]] should contain theSameElementsAs codes.map(_.copy(value = "*****")).take(2)
    }
  }

  they should "crate bootstrap codes for existing devices" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().put)).await

    Put(s"/devices/codes/own/for-device/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      val code = responseAs[DeviceBootstrapCode]
      code.value should be("test-code")
      code.owner should be(currentUser.id)
      code.target should be(Left(devices.head.id))
    }
  }

  they should "crate bootstrap codes for new devices" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    val request = CreateDeviceOwn(name = "test-device", limits = None)

    Put("/devices/codes/own/for-device/new")
      .withEntity(Marshal(request).to[RequestEntity].await) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      val code = responseAs[DeviceBootstrapCode]
      code.value should be("test-code")
      code.owner should be(currentUser.id)
      code.target should be(Right(request))
    }
  }

  they should "delete existing device bootstrap codes" in withRetry {
    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().put)).await

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Delete(s"/devices/codes/own/${bootstrapCode.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  they should "not delete missing device bootstrap codes" in withRetry {
    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().put)).await

    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)

    Delete(s"/devices/codes/own/${bootstrapCode.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  they should "successfully execute bootstrap for existing devices" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().put(user).await
    Future.sequence(devices.map(fixtures.deviceStore.manage().put)).await

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      responseAs[DeviceBootstrapParameters] should be(
        baseParams
          .withDeviceInfo(
            device = devices.head.id.toString,
            nodeId = devices.head.node.toString,
            clientId = "test-client-id",
            clientSecret = "test-secret"
          )
          .withUserInfo(
            user = user.id.toString,
            userSalt = user.salt
          )
      )
    }
  }

  they should "successfully execute bootstrap for new devices" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats._

    val request = CreateDeviceOwn(name = "test-device", limits = None)

    val fixtures = new TestFixtures {
      override def currentBootstrapCode(): DeviceBootstrapCode = bootstrapCode.copy(
        target = Right(request)
      )
    }

    fixtures.userStore.manage().put(user).await
    fixtures.deviceStore.view().list().await should be(empty)
    fixtures.nodeStore.view().list().await should be(empty)

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      val actualParams = responseAs[DeviceBootstrapParameters]

      val device = fixtures.deviceStore.view().list().await.head
      val (node, _) = fixtures.nodeStore.view().list().await.head

      actualParams should be(
        baseParams
          .withDeviceInfo(
            device = device.id.toString,
            nodeId = node.toString,
            clientId = "test-client-id",
            clientSecret = "test-secret"
          )
          .withUserInfo(
            user = user.id.toString,
            userSalt = user.salt
          )
      )

      fixtures.deviceStore.view().list().await should not be empty
      fixtures.nodeStore.view().list().await should not be empty
    }
  }

  they should "fail to execute device bootstrap if the target device is missing" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().put(user).await

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))
    }
  }

  they should "fail to execute device bootstrap if the target owner is missing" in withRetry {
    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().put)).await

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DeviceBootstrapSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    def currentBootstrapCode(): DeviceBootstrapCode = bootstrapCode

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

    lazy val bootstrap = new DeviceBootstrap(bootstrapContext)

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        userStore.view(),
        userStore.viewSelf(),
        deviceStore.view(),
        deviceStore.viewSelf(),
        deviceStore.manage(),
        deviceStore.manageSelf(),
        bootstrapCodeStore.view(),
        bootstrapCodeStore.viewSelf(),
        bootstrapCodeStore.manage(),
        bootstrapCodeStore.manageSelf(),
        nodeStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route =
      pathPrefix("devices") {
        concat(
          pathPrefix("codes") {
            bootstrap.codes
          },
          pathPrefix("execute") {
            bootstrap.execute(currentBootstrapCode())
          }
        )
      }
  }

  private val user = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty,
    created = Instant.now(),
    updated = Instant.now()
  )

  private implicit val currentUser: CurrentUser = CurrentUser(user.id)

  private val devices = Seq(
    Device(
      id = Device.generateId(),
      name = "test-device-0",
      node = Node.generateId(),
      owner = user.id,
      active = true,
      limits = None,
      created = Instant.now(),
      updated = Instant.now()
    ),
    Device(
      id = Device.generateId(),
      name = "test-device-1",
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None,
      created = Instant.now(),
      updated = Instant.now()
    )
  )

  private val bootstrapCode: DeviceBootstrapCode = DeviceBootstrapCode(
    value = "test-code",
    owner = user.id,
    device = devices.head.id,
    expiresAt = Instant.now().plusSeconds(42)
  )

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
}
