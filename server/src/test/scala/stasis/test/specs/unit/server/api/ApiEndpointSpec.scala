package stasis.test.specs.unit.server.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.json.JsArray
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.nodes.NodeStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.routing.{Node, NodeProxy}
import stasis.server.api.ApiEndpoint
import stasis.server.model.datasets.{DatasetDefinitionStore, DatasetEntryStore}
import stasis.server.model.devices.DeviceStore
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.model.reservations.ServerReservationStore
import stasis.server.model.schedules.ScheduleStore
import stasis.server.model.staging.ServerStagingStore
import stasis.server.model.users.UserStore
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator
import stasis.shared.api.responses.Ping
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.{MockGrpcEndpointClient, MockHttpEndpointClient}
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.{MockCrateStore, MockNodeStore, MockReservationStore}
import stasis.test.specs.unit.server.Secrets
import stasis.test.specs.unit.server.model.mocks._
import stasis.test.specs.unit.server.security.mocks.{MockResourceProvider, MockUserAuthenticator, MockUserCredentialsManager}

import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

class ApiEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  "An ApiEndpoint" should "successfully authenticate users" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val user = User(
      id = testUser,
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty
    )

    fixtures.userStore.manage().create(user).await

    Get(s"/users").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should be(Seq(user))
    }
  }

  it should "fail to authenticate users with no credentials" in {
    val fixtures = new TestFixtures {}

    Get("/users") ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with invalid credentials" in {
    val fixtures = new TestFixtures {}

    Get("/users")
      .addCredentials(testCredentials.copy(username = "invalid-username")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a user with invalid password" in {
    val fixtures = new TestFixtures {}

    Get("/users")
      .addCredentials(testCredentials.copy(password = "invalid-password")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide routes for dataset definitions" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val definition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "test-definition",
      device = Device.generateId(),
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

    Get("/datasets/definitions").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should be(Seq(definition))
    }
  }

  it should "provide routes for dataset entries" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

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
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val user = User(
      id = testUser,
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty
    )

    fixtures.userStore.manage().create(user).await

    Get("/users").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should be(Seq(user))
    }
  }

  it should "provide routes for devices" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val device = Device(
      id = Device.generateId(),
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None
    )

    fixtures.deviceStore.manage().create(device).await

    Get("/devices").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should be(Seq(device))
    }
  }

  it should "provide routes for schedules" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val schedule = Schedule(
      id = Schedule.generateId(),
      info = "test-schedule",
      isPublic = true,
      start = LocalDateTime.now(),
      interval = 3.seconds
    )

    fixtures.scheduleStore.manage().create(schedule).await

    Get("/schedules").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should be(Seq(schedule))
    }
  }

  it should "provide routes for nodes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val node = Generators.generateLocalNode

    fixtures.nodeStore.put(node).await

    Get("/nodes").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Node]] should be(Seq(node))
    }
  }

  it should "provide routes for reservations" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val reservation = Generators.generateReservation

    fixtures.reservationStore.put(reservation).await

    Get("/reservations").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CrateStorageReservation]] should be(Seq(reservation))
    }
  }

  it should "provide routes for staging operations" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val testContent = ByteString("some value")

    val testManifest = Manifest(
      crate = Crate.generateId(),
      size = testContent.size.toLong,
      copies = 4,
      source = Node.generateId(),
      origin = Node.generateId()
    )

    val destinations: Map[Node, Int] = Map(
      Node.Remote.Http(Node.generateId(), address = HttpEndpointAddress("localhost:8000")) -> 1
    )

    val proxy: NodeProxy = new NodeProxy(
      httpClient = new MockHttpEndpointClient(),
      grpcClient = new MockGrpcEndpointClient()
    ) {
      override protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
        Future.failed(new IllegalStateException("Operation not supported"))
    }

    fixtures.stagingStore
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = proxy
      )
      .await

    Get("/staging").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsArray].value.map(_ \ "crate").map(_.as[UUID]) should be(Seq(testManifest.crate))
    }
  }

  it should "provide service routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    Get("/service/ping").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  it should "handle authorization failures reported by routes" in {
    val endpoint = new ApiEndpoint(
      resourceProvider = new MockResourceProvider(Set.empty),
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword),
      userCredentialsManager = MockUserCredentialsManager(),
      secretsConfig = testSecretsConfig
    )

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
          uri = s"http://localhost:$endpointPort/users/self"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Forbidden)
      }
  }

  it should "handle generic failures reported by routes" in {
    val userStore: UserStore = MockUserStore()

    val endpoint = new ApiEndpoint(
      resourceProvider = new MockResourceProvider(Set(userStore.manageSelf())),
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword),
      userCredentialsManager = MockUserCredentialsManager(),
      secretsConfig = testSecretsConfig
    )

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$endpointPort/users/self/deactivate"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response.entity).to[String].await should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  it should "reject requests with invalid entities" in {
    val userStore: UserStore = MockUserStore()

    val endpoint = new ApiEndpoint(
      resourceProvider = new MockResourceProvider(Set(userStore.manageSelf())),
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword),
      userCredentialsManager = MockUserCredentialsManager(),
      secretsConfig = testSecretsConfig
    )

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:$endpointPort/users",
          entity = HttpEntity(ContentTypes.`application/json`, "{\"a\":1}")
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response.entity).to[String].await should startWith(
          "Provided data is invalid or malformed"
        )
      }
  }

  private implicit val typedSystem: akka.actor.typed.ActorSystem[SpawnProtocol.Command] = akka.actor.typed.ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ApiEndpointSpec_Typed"
  )

  private trait TestFixtures {
    lazy val definitionStore: DatasetDefinitionStore = MockDatasetDefinitionStore()
    lazy val entryStore: DatasetEntryStore = MockDatasetEntryStore()
    lazy val deviceStore: DeviceStore = MockDeviceStore()
    lazy val scheduleStore: ScheduleStore = MockScheduleStore()
    lazy val userStore: UserStore = MockUserStore()
    lazy val serverNodeStore: ServerNodeStore = ServerNodeStore(nodeStore)
    lazy val serverReservationStore: ServerReservationStore = ServerReservationStore(reservationStore)
    lazy val serverStagingStore: ServerStagingStore = ServerStagingStore(stagingStore)

    lazy val nodeStore: NodeStore = new MockNodeStore()
    lazy val reservationStore: ReservationStore = new MockReservationStore()
    lazy val stagingStore: StagingStore = new StagingStore(
      crateStore = new MockCrateStore(),
      destagingDelay = 1.second
    )

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
        userStore.viewSelf(),
        serverNodeStore.manage(),
        serverNodeStore.view(),
        serverReservationStore.view(),
        serverStagingStore.manage(),
        serverStagingStore.view()
      )
    )

    lazy val authenticator: UserAuthenticator = new MockUserAuthenticator(testUser.toString, testPassword)

    lazy val endpoint: ApiEndpoint = new ApiEndpoint(
      resourceProvider = provider,
      authenticator = authenticator,
      userCredentialsManager = MockUserCredentialsManager(),
      secretsConfig = testSecretsConfig
    )
  }

  private val testUser = User.generateId()
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser.toString, password = testPassword)

  private val ports: mutable.Queue[Int] = (21000 to 21100).to(mutable.Queue)
}
