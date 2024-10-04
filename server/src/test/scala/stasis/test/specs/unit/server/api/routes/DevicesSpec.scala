package stasis.test.specs.unit.server.api.routes

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.`Content-Type`
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.layers.telemetry.TelemetryContext
import stasis.server.api.routes.Devices
import stasis.server.api.routes.RoutesContext
import stasis.server.model.devices.DeviceKeyStore
import stasis.server.model.devices.DeviceStore
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.model.users.UserStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.shared.api.requests._
import stasis.shared.api.responses.CreatedDevice
import stasis.shared.api.responses.DeletedDevice
import stasis.shared.api.responses.DeletedDeviceKey
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.mocks.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.model.mocks.MockDeviceKeyStore
import stasis.test.specs.unit.server.model.mocks.MockDeviceStore
import stasis.test.specs.unit.server.model.mocks.MockUserStore
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class DevicesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "Devices routes (full permissions)" should "respond with all devices" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should contain theSameElementsAs devices
    }
  }

  they should "create new devices" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user).await

    Post("/").withEntity(createRequestPrivileged) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.deviceStore
        .view()
        .get(entityAs[CreatedDevice].device)
        .map(_.isDefined should be(true))
    }
  }

  they should "fail to create new devices for missing users" in withRetry {
    val fixtures = new TestFixtures {}

    Post("/").withEntity(createRequestPrivileged) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "respond with existing devices" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(devices.head).await

    Get(s"/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Device] should be(devices.head)
    }
  }

  they should "fail if a device is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/${Device.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing devices' state" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user.copy(id = devices.head.owner))
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_.map(_.active) should be(Some(updateRequestState.active)))
    }
  }

  they should "update existing devices' limits" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user.copy(id = devices.head.owner))
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_.map(_.limits) should be(Some(updateRequestLimits.limits)))
    }
  }

  they should "fail to update state if a device is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/${Device.generateId()}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if a device is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/${Device.generateId()}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update state if the device owner is missing" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if the device owner is missing" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing devices" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Delete(s"/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDevice] should be(DeletedDevice(existing = true))

      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing devices" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDevice] should be(DeletedDevice(existing = false))
    }
  }

  they should "respond with device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device)

    fixtures.deviceStore.manage().create(devices.head).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device), deviceKey).await

    Get(s"/$device/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeviceKey] should be(deviceKey.copy(value = ByteString.empty))
    }
  }

  they should "fail to retrieve missing device keys" in withRetry {
    val fixtures = new TestFixtures {}

    Get(s"/${devices.head.id}/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device)

    fixtures.deviceStore.manage().create(devices.head).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device), deviceKey).await

    Delete(s"/$device/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDeviceKey] should be(DeletedDeviceKey(existing = true))

      fixtures.deviceKeyStore
        .view()
        .get(device)
        .map(_ should be(None))
    }
  }

  they should "not delete missing device keys" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${devices.head.id}/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDeviceKey] should be(DeletedDeviceKey(existing = false))
    }
  }

  they should "respond with list of device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device)

    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device), deviceKey).await

    Get(s"/keys") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceKey]] should be(Seq(deviceKey.copy(value = ByteString.empty)))
    }
  }

  "Devices routes (self permissions)" should "respond with all devices" in withRetry {
    val fixtures = new TestFixtures {}
    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    Get("/own") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should contain theSameElementsAs devices.take(1)
    }
  }

  they should "create new devices" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user).await

    Post("/own")
      .withEntity(createRequestOwn) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.deviceStore
        .view()
        .get(entityAs[CreatedDevice].device)
        .map(_.isDefined should be(true))
    }
  }

  they should "fail to create new devices for missing users" in withRetry {
    val fixtures = new TestFixtures {}

    Post("/own")
      .withEntity(createRequestOwn) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "respond with existing devices" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(devices.head).await

    Get(s"/own/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Device] should be(devices.head)
    }
  }

  they should "fail if a device is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Get(s"/own/${Device.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing devices' state" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user).await
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_.map(_.active) should be(Some(updateRequestState.active)))
    }
  }

  they should "update existing devices' limits" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user).await
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_.map(_.limits) should be(Some(updateRequestLimits.limits)))
    }
  }

  they should "fail to update state if a device is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/own/${Device.generateId()}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if a device is missing" in withRetry {
    val fixtures = new TestFixtures {}
    Put(s"/own/${Device.generateId()}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update state if the device owner is missing" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if the device owner is missing" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing devices" in withRetry {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Delete(s"/own/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDevice] should be(DeletedDevice(existing = true))

      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_ should be(None))
    }
  }

  they should "not delete missing devices" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/own/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDevice] should be(DeletedDevice(existing = false))
    }
  }

  they should "support checking if device keys exist" in withRetry {
    val fixtures = new TestFixtures {}

    val device1 = devices.head.id
    val device2 = devices.last.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device1)

    fixtures.deviceStore.manage().create(devices.head).await
    fixtures.deviceStore.manage().create(devices.last.copy(owner = user.id)).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device1), deviceKey).await

    Head(s"/own/$device1/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      header[`Content-Type`].map(_.contentType) should be(Some(ContentTypes.`application/octet-stream`))

      val actualKey = response.entity.dataBytes.runFold(ByteString.empty)(_ concat _).await
      actualKey should be(ByteString.empty)
    }

    Head(s"/own/$device2/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "respond with device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device)

    fixtures.deviceStore.manage().create(devices.head).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device), deviceKey).await

    Get(s"/own/$device/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      header[`Content-Type`].map(_.contentType) should be(Some(ContentTypes.`application/octet-stream`))

      val actualKey = response.entity.dataBytes.runFold(ByteString.empty)(_ concat _).await
      actualKey should be(deviceKey.value)
    }
  }

  they should "fail to retrieve missing device keys" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(devices.head).await

    Get(s"/own/${devices.head.id}/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    fixtures.userStore.manage().create(user).await
    fixtures.deviceStore.manage().create(devices.head).await

    fixtures.deviceKeyStore.view().list().await should be(empty)

    Put(s"/own/$device/key").withEntity(ByteString("test-key")) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.deviceKeyStore.viewSelf().get(Seq(device), device).await match {
        case Some(actualKey) =>
          actualKey should be(DeviceKey(value = ByteString("test-key"), owner = user.id, device = device))

        case None =>
          fail("Expected key but none was found")
      }
    }
  }

  they should "fail to update device keys with no key data" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    fixtures.userStore.manage().create(user).await
    fixtures.deviceStore.manage().create(devices.head).await

    fixtures.deviceKeyStore.view().list().await should be(empty)

    Put(s"/own/$device/key") ~> Route.seal(fixtures.routes) ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update device keys for missing users" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    fixtures.deviceStore.manage().create(devices.head).await

    fixtures.deviceKeyStore.view().list().await should be(empty)

    Put(s"/own/$device/key").withEntity(ByteString("test-key")) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update device keys for missing devices" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.userStore.manage().create(user).await
    fixtures.deviceKeyStore.view().list().await should be(empty)

    Put(s"/own/${devices.head.id}/key").withEntity(ByteString("test-key")) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device)

    fixtures.deviceStore.manage().create(devices.head).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device), deviceKey).await

    Delete(s"/own/$device/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDeviceKey] should be(DeletedDeviceKey(existing = true))

      fixtures.deviceKeyStore
        .view()
        .get(device)
        .map(_ should be(None))
    }
  }

  they should "not delete missing device keys" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(devices.head).await

    Delete(s"/own/${devices.head.id}/key") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDeviceKey] should be(DeletedDeviceKey(existing = false))
    }
  }

  they should "respond with list of device keys" in withRetry {
    val fixtures = new TestFixtures {}

    val device = devices.head.id

    val deviceKey = DeviceKey(value = ByteString("test-key"), owner = user.id, device = device)

    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await
    fixtures.deviceKeyStore.manageSelf().put(Seq(device), deviceKey).await

    Get(s"/own/keys") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DeviceKey]] should be(Seq(deviceKey.copy(value = ByteString.empty)))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "DevicesSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val userStore: UserStore = MockUserStore()

    lazy val deviceStore: DeviceStore = MockDeviceStore()
    lazy val deviceKeyStore: DeviceKeyStore = MockDeviceKeyStore()

    lazy val nodeStore: NodeStore = new MockNodeStore()
    lazy val serverNodeStore: ServerNodeStore = ServerNodeStore(nodeStore)

    implicit lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        userStore.view(),
        userStore.viewSelf(),
        deviceStore.view(),
        deviceStore.viewSelf(),
        deviceStore.manage(),
        deviceStore.manageSelf(),
        deviceKeyStore.view(),
        deviceKeyStore.viewSelf(),
        deviceKeyStore.manage(),
        deviceKeyStore.manageSelf(),
        serverNodeStore.manageSelf()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Devices().routes
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
      name = "test-device-0",
      node = Node.generateId(),
      owner = user.id,
      active = true,
      limits = None
    ),
    Device(
      id = Device.generateId(),
      name = "test-device-1",
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None
    )
  )

  private val createRequestPrivileged = CreateDevicePrivileged(
    name = "test-device",
    node = Some(Node.generateId()),
    owner = user.id,
    limits = None
  )

  private val createRequestOwn = CreateDeviceOwn(
    name = "test-device",
    limits = None
  )

  private val updateRequestLimits = UpdateDeviceLimits(
    limits = Some(
      Device.Limits(
        maxCrates = 1,
        maxStorage = 2,
        maxStoragePerCrate = 3,
        maxRetention = 5.seconds,
        minRetention = 5.seconds
      )
    )
  )

  private val updateRequestState = UpdateDeviceState(
    active = false
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateDevicePrivileged): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def createRequestToEntity(request: CreateDeviceOwn): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateDeviceLimits): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateDeviceState): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
