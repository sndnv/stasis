package stasis.test.specs.unit.server.api

import java.time.{Instant, LocalTime}

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import stasis.core.packaging.Crate
import stasis.core.routing.Node
import stasis.server.api.ServerEndpoint
import stasis.server.model.datasets.{DatasetDefinitionStore, DatasetEntryStore}
import stasis.server.model.devices.DeviceStore
import stasis.server.model.schedules.ScheduleStore
import stasis.server.model.users.UserStore
import stasis.server.security.{ResourceProvider, UserAuthenticator}
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.server.model.mocks._
import stasis.test.specs.unit.server.security.mocks.{MockResourceProvider, MockUserAuthenticator}

class ServerEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import scala.language.implicitConversions

  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.shared.api.Formats._

  private implicit val untypedSystem: ActorSystem = ActorSystem(name = "ServerEndpointSpec")
  private implicit val log: LoggingAdapter = Logging(untypedSystem, this.getClass.getName)

  private trait TestFixtures {
    lazy val definitionStore: DatasetDefinitionStore = new MockDatasetDefinitionStore()
    lazy val entryStore: DatasetEntryStore = new MockDatasetEntryStore()
    lazy val deviceStore: DeviceStore = new MockDeviceStore()
    lazy val scheduleStore: ScheduleStore = new MockScheduleStore()
    lazy val userStore: UserStore = new MockUserStore()

    lazy val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        definitionStore.manage(),
        definitionStore.manageSelf(),
        definitionStore.view(),
        definitionStore.viewSelf(),
        entryStore.manage(),
        entryStore.manageSelf(),
        entryStore.view(),
        entryStore.viewSelf(),
        deviceStore.manage(),
        deviceStore.manageSelf(),
        deviceStore.view(),
        deviceStore.viewSelf(),
        scheduleStore.manage(),
        scheduleStore.view(),
        userStore.manage(),
        userStore.manageSelf(),
        userStore.view(),
        userStore.viewSelf()
      )
    )

    lazy val authenticator: UserAuthenticator = new MockUserAuthenticator(testUser.toString, testPassword)

    lazy val endpoint: ServerEndpoint = new ServerEndpoint(provider, authenticator)
  }

  private val testUser = User.generateId()
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser.toString, password = testPassword)

  private val ports: mutable.Queue[Int] = (21000 to 21100).to[mutable.Queue]

  "A ServerEndpoint" should "successfully authenticate a user" in {
    val fixtures = new TestFixtures {}

    val user = User(
      id = testUser,
      isActive = true,
      limits = None,
      permissions = Set.empty
    )

    fixtures.userStore.manage().create(user).await

    Get(s"/users").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should be(Seq(user))
    }
  }

  it should "fail to authenticate a user with no credentials" in {
    val fixtures = new TestFixtures {}

    Get(s"/users") ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a user with invalid username" in {
    val fixtures = new TestFixtures {}

    Get(s"/users")
      .addCredentials(testCredentials.copy(username = "invalid-username")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a user with invalid password" in {
    val fixtures = new TestFixtures {}

    Get(s"/users")
      .addCredentials(testCredentials.copy(password = "invalid-password")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide routes for dataset definitions" in {
    val fixtures = new TestFixtures {}

    val definition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      device = Device.generateId(),
      schedule = None,
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(
        DatasetDefinition.Retention.Policy.All,
        duration = 1.second
      ),
      removedVersions = DatasetDefinition.Retention(
        DatasetDefinition.Retention.Policy.LatestOnly,
        duration = 1.second
      )
    )

    fixtures.definitionStore.manage().create(definition).await

    Get(s"/datasets/definitions").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should be(Seq(definition))
    }
  }

  it should "provide routes for dataset entries" in {
    val fixtures = new TestFixtures {}

    val definitionId = DatasetDefinition.generateId()

    val entry = DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = definitionId,
      device = Device.generateId(),
      data = Set.empty,
      metadata = Crate.generateId(),
      created = Instant.now()
    )

    fixtures.entryStore.manage().create(entry).await

    Get(s"/datasets/entries/for-definition/$definitionId")
      .addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetEntry]] should be(Seq(entry))
    }
  }

  it should "provide routes for users" in {
    val fixtures = new TestFixtures {}

    val user = User(
      id = testUser,
      isActive = true,
      limits = None,
      permissions = Set.empty
    )

    fixtures.userStore.manage().create(user).await

    Get(s"/users").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should be(Seq(user))
    }
  }

  it should "provide routes for devices" in {
    val fixtures = new TestFixtures {}

    val device = Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = User.generateId(),
      isActive = true,
      limits = None
    )

    fixtures.deviceStore.manage().create(device).await

    Get(s"/devices").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should be(Seq(device))
    }
  }

  it should "provide routes for schedules" in {
    val fixtures = new TestFixtures {}

    val schedule = Schedule(
      id = Schedule.generateId(),
      process = Schedule.Process.Backup,
      instant = LocalTime.now(),
      interval = 3.seconds,
      missed = Schedule.MissedAction.ExecuteImmediately,
      overlap = Schedule.OverlapAction.ExecuteAnyway
    )

    fixtures.scheduleStore.manage().create(schedule).await

    Get(s"/schedules").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should be(Seq(schedule))
    }
  }

  it should "handle authorization failures reported by routes" in {
    val endpoint = new ServerEndpoint(
      resourceProvider = new MockResourceProvider(Set.empty),
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword)
    )

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(hostname = "localhost", port = endpointPort)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/users/self"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Forbidden)
      }
  }

  it should "handle generic failures reported by routes" in {
    val userStore: UserStore = new MockUserStore()

    val endpoint = new ServerEndpoint(
      resourceProvider = new MockResourceProvider(Set(userStore.manageSelf())),
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword)
    )

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(hostname = "localhost", port = endpointPort)

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$endpointPort/users/self/deactivate"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
      }
  }
}
