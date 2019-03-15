package stasis.test.specs.unit.server.api.routes
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.core.routing.Node
import stasis.server.api.requests._
import stasis.server.api.responses.{CreatedDevice, DeletedDevice}
import stasis.server.api.routes.Devices
import stasis.server.model.devices.{Device, DeviceStore}
import stasis.server.model.users.{User, UserStore}
import stasis.server.security.ResourceProvider
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks.{MockDeviceStore, MockUserStore}
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

import scala.concurrent.Future
import scala.concurrent.duration._

class DevicesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.server.api.Formats._

  import scala.language.implicitConversions

  private implicit val untypedSystem: ActorSystem = ActorSystem(name = "DevicesSpec")
  private implicit val log: LoggingAdapter = Logging(untypedSystem, this.getClass.getName)

  private trait TestFixtures {
    lazy val userStore: UserStore = new MockUserStore()

    lazy val deviceStore: DeviceStore = new MockDeviceStore()

    lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        userStore.view(),
        userStore.viewSelf(),
        deviceStore.view(),
        deviceStore.viewSelf(),
        deviceStore.manage(),
        deviceStore.manageSelf()
      )
    )

    lazy val routes: Route = Devices(provider, user.id)
  }

  private val user = User(
    id = User.generateId(),
    isActive = true,
    limits = None,
    permissions = Set.empty
  )

  private val devices = Seq(
    Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = user.id,
      isActive = true,
      limits = None
    ),
    Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = User.generateId(),
      isActive = true,
      limits = None
    )
  )

  private val createRequestPrivileged = CreateDevicePrivileged(
    node = Node.generateId(),
    owner = user.id,
    limits = None
  )

  private val createRequestOwn = CreateDeviceOwn(
    node = Node.generateId(),
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
    isActive = false
  )

  private implicit def createRequestToEntity(request: CreateDevicePrivileged): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def createRequestToEntity(request: CreateDeviceOwn): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateDeviceLimits): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateDeviceState): RequestEntity =
    Marshal(request).to[RequestEntity].await

  "Devices routes (full permissions)" should "respond with all devices" in {
    val fixtures = new TestFixtures {}
    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should contain theSameElementsAs devices
    }
  }

  they should "create new devices" in {
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

  they should "fail to create new devices for missing users" in {
    val fixtures = new TestFixtures {}

    Post("/").withEntity(createRequestPrivileged) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "respond with existing devices" in {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(devices.head).await

    Get(s"/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Device] should be(devices.head)
    }
  }

  they should "fail if a device is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/${Device.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing devices' state" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user.copy(id = devices.head.owner))
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_.map(_.isActive) should be(Some(updateRequestState.isActive)))
    }
  }

  they should "update existing devices' limits" in {
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

  they should "fail to update state if a device is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${Device.generateId()}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if a device is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${Device.generateId()}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update state if the device owner is missing" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if the device owner is missing" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/${devices.head.id}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing devices" in {
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

  they should "not delete missing devices" in {
    val fixtures = new TestFixtures {}

    Delete(s"/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDevice] should be(DeletedDevice(existing = false))
    }
  }

  "Devices routes (self permissions)" should "respond with all devices" in {
    val fixtures = new TestFixtures {}
    Future.sequence(devices.map(fixtures.deviceStore.manage().create)).await

    Get("/own") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should contain theSameElementsAs devices.take(1)
    }
  }

  they should "create new devices" in {
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

  they should "fail to create new devices for missing users" in {
    val fixtures = new TestFixtures {}

    Post("/own")
      .withEntity(createRequestOwn) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "respond with existing devices" in {
    val fixtures = new TestFixtures {}

    fixtures.deviceStore.manage().create(devices.head).await

    Get(s"/own/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Device] should be(devices.head)
    }
  }

  they should "fail if a device is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/own/${Device.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing devices' state" in {
    val fixtures = new TestFixtures {}
    fixtures.userStore.manage().create(user).await
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      fixtures.deviceStore
        .view()
        .get(devices.head.id)
        .map(_.map(_.isActive) should be(Some(updateRequestState.isActive)))
    }
  }

  they should "update existing devices' limits" in {
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

  they should "fail to update state if a device is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/own/${Device.generateId()}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if a device is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/own/${Device.generateId()}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update state if the device owner is missing" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/state")
      .withEntity(updateRequestState) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "fail to update limits if the device owner is missing" in {
    val fixtures = new TestFixtures {}
    fixtures.deviceStore.manage().create(devices.head).await

    Put(s"/own/${devices.head.id}/limits")
      .withEntity(updateRequestLimits) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing devices" in {
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

  they should "not delete missing devices" in {
    val fixtures = new TestFixtures {}

    Delete(s"/own/${devices.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedDevice] should be(DeletedDevice(existing = false))
    }
  }
}
