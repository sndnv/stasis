package stasis.test.specs.unit.server.api.routes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import stasis.core.routing.Node
import stasis.server.api.routes.{DeviceBootstrap, RoutesContext}
import stasis.server.model.devices.{DeviceBootstrapCodeStore, DeviceStore}
import stasis.server.model.users.UserStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.model.devices.{Device, DeviceBootstrapCode, DeviceBootstrapParameters}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.Secrets
import stasis.test.specs.unit.server.model.mocks._
import stasis.test.specs.unit.server.security.mocks._
import stasis.test.specs.unit.shared.model.Generators

import java.time.Instant
import scala.concurrent.Future

class DeviceBootstrapSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  "DeviceBootstrap routes (full permissions)" should "respond with all device bootstrap codes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
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

  they should "delete existing device bootstrap codes" in {
    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Delete(s"/devices/codes/for-device/${bootstrapCode.device.toString}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  they should "not delete missing device bootstrap codes" in {
    val fixtures = new TestFixtures {}

    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)

    Delete(s"/devices/codes/for-device/${bootstrapCode.device.toString}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  "DeviceBootstrap routes (self permissions)" should "respond with all device bootstrap codes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    val codes = Seq(
      Generators.generateDeviceBootstrapCode.copy(device = devices.head.id),
      Generators.generateDeviceBootstrapCode.copy(device = devices.head.id),
      Generators.generateDeviceBootstrapCode
    )

    Future.sequence(codes.map(fixtures.bootstrapCodeStore.manage().put)).await
    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    Get("/devices/codes/own") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceBootstrapCode]] should contain theSameElementsAs codes.map(_.copy(value = "*****")).take(2)
    }
  }

  they should "crate new device bootstrap codes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    Put(s"/devices/codes/own/for-device/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      val code = responseAs[DeviceBootstrapCode]
      code.value should be("test-code")
      code.owner should be(currentUser.id)
      code.device should be(devices.head.id)
    }
  }

  they should "delete existing device bootstrap codes" in {
    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Delete(s"/devices/codes/own/for-device/${bootstrapCode.device.toString}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  they should "not delete missing device bootstrap codes" in {
    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)

    Delete(s"/devices/codes/own/for-device/${bootstrapCode.device.toString}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  they should "successfully execute device bootstrap" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.shared.api.Formats._

    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().create(user).await
    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)

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

  they should "fail to execute device bootstrap if the target device is missing" in {
    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().create(user).await

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)
      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))
    }
  }

  they should "fail to execute device bootstrap if the target owner is missing" in {
    val fixtures = new TestFixtures {}

    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    fixtures.bootstrapCodeStore.manage().put(bootstrapCode).await
    fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(Some(bootstrapCode))

    Put("/devices/execute") ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)

      fixtures.bootstrapCodeStore.view().get(bootstrapCode.value).await should be(None)
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DeviceBootstrapSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

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
        bootstrapCodeStore.manageSelf()
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
            bootstrap.execute(bootstrapCode)
          }
        )
      }
  }

  private val user = User(
    id = User.generateId(),
    salt = "test-salt",
    active = true,
    limits = None,
    permissions = Set.empty
  )

  private implicit val currentUser: CurrentUser = CurrentUser(user.id)

  private val devices = Seq(
    Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = user.id,
      active = true,
      limits = None
    ),
    Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None
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
}
