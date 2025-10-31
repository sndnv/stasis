package stasis.server.api

import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.libs.json.JsArray

import stasis.core.discovery.ServiceDiscoveryRequest
import stasis.core.discovery.ServiceDiscoveryResult
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.persistence.nodes.NodeStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.routing.Node
import stasis.core.routing.NodeProxy
import io.github.sndnv.layers.api.MessageResponse
import io.github.sndnv.layers.events.EventCollector
import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.TelemetryContext
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry

import stasis.server.Secrets
import stasis.server.persistence.analytics.AnalyticsEntryStore
import stasis.server.persistence.analytics.MockAnalyticsEntryStore
import stasis.server.persistence.datasets.DatasetDefinitionStore
import stasis.server.persistence.datasets.DatasetEntryStore
import stasis.server.persistence.datasets.MockDatasetDefinitionStore
import stasis.server.persistence.datasets.MockDatasetEntryStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.devices.MockDeviceStore
import stasis.server.persistence.manifests.ServerManifestStore
import stasis.server.persistence.nodes.ServerNodeStore
import stasis.server.persistence.reservations.ServerReservationStore
import stasis.server.persistence.schedules.MockScheduleStore
import stasis.server.persistence.schedules.ScheduleStore
import stasis.server.persistence.staging.ServerStagingStore
import stasis.server.persistence.users.MockUserStore
import stasis.server.persistence.users.UserStore
import stasis.server.security.ResourceProvider
import stasis.server.security.authenticators.UserAuthenticator
import stasis.server.security.mocks.MockResourceProvider
import stasis.server.security.mocks.MockUserAuthenticator
import stasis.server.security.mocks.MockUserCredentialsManager
import stasis.shared.api.requests.CreateAnalyticsEntry
import stasis.shared.api.responses.CreatedAnalyticsEntry
import stasis.shared.api.responses.Ping
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockGrpcEndpointClient
import stasis.test.specs.unit.core.networking.mocks.MockHttpEndpointClient
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.crates.MockCrateStore
import stasis.test.specs.unit.core.persistence.manifests.MockManifestStore
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.persistence.reservations.MockReservationStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class ApiEndpointSpec extends AsyncUnitSpec with ScalatestRouteTest with Secrets {
  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  "An ApiEndpoint (v1)" should "successfully authenticate users" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val user = User(
      id = testUser,
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty,
      created = Instant.now(),
      updated = Instant.now()
    )

    fixtures.userStore.manage().put(user).await

    Get(s"/users").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should be(Seq(user))
    }
  }

  it should "fail to authenticate users with no credentials" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/users") ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with invalid credentials" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/users")
      .addCredentials(testCredentials.copy(username = "invalid-username")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a user with invalid password" in withRetry {
    val fixtures = new TestFixtures {}

    Get("/users")
      .addCredentials(testCredentials.copy(password = "invalid-password")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide routes for dataset definitions" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val now = Instant.now()

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
      ),
      created = now,
      updated = now
    )

    fixtures.definitionStore.manage().put(definition).await

    Get("/datasets/definitions").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[DatasetDefinition]] should be(Seq(definition))
    }
  }

  it should "provide routes for dataset entries" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

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

  it should "provide routes for users" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val user = User(
      id = testUser,
      salt = "test-salt",
      active = true,
      limits = None,
      permissions = Set.empty,
      created = Instant.now(),
      updated = Instant.now()
    )

    fixtures.userStore.manage().put(user).await

    Get("/users").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[User]] should be(Seq(user))
    }
  }

  it should "provide routes for devices" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val device = Device(
      id = Device.generateId(),
      name = "test-device",
      node = Node.generateId(),
      owner = User.generateId(),
      active = true,
      limits = None,
      created = Instant.now(),
      updated = Instant.now()
    )

    fixtures.deviceStore.manage().put(device).await

    Get("/devices").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Device]] should be(Seq(device))
    }
  }

  it should "provide routes for schedules" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val schedule = Schedule(
      id = Schedule.generateId(),
      info = "test-schedule",
      isPublic = true,
      start = LocalDateTime.now(),
      interval = 3.seconds,
      created = Instant.now(),
      updated = Instant.now()
    )

    fixtures.scheduleStore.manage().put(schedule).await

    Get("/schedules").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Schedule]] should be(Seq(schedule))
    }
  }

  it should "provide routes for nodes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val node = Generators.generateLocalNode

    fixtures.nodeStore.put(node).await

    Get("/nodes").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Node]] should be(Seq(node))
    }
  }

  it should "provide routes for manifests" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val manifest = Generators.generateManifest

    fixtures.manifestStore.put(manifest).await

    Get(s"/manifests/${manifest.crate}").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Manifest] should be(manifest)
    }
  }

  it should "provide routes for reservations" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val reservation = Generators.generateReservation

    fixtures.reservationStore.put(reservation).await

    Get("/reservations").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CrateStorageReservation]] should be(Seq(reservation))
    }
  }

  it should "provide routes for staging operations" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

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
      Node.Remote.Http(
        id = Node.generateId(),
        address = HttpEndpointAddress("localhost:8000"),
        storageAllowed = true,
        created = Instant.now(),
        updated = Instant.now()
      ) -> 1
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

  it should "provide service routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    Get("/service/ping").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[Ping]
    }
  }

  it should "provide analytics routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    import stasis.shared.api.Formats.createAnalyticsEntryRequestFormat

    val fixtures = new TestFixtures {}

    val entity = Marshal(
      CreateAnalyticsEntry(entry = AnalyticsEntry.collected(app = ApplicationInformation.none))
    ).to[RequestEntity].await

    Post("/analytics").withEntity(entity).addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[CreatedAnalyticsEntry]
    }
  }

  it should "provide discovery routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val entity = Marshal(ServiceDiscoveryRequest(isInitialRequest = true, attributes = Map.empty)).to[RequestEntity].await

    Post("/discovery/provide").withEntity(entity).addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      noException should be thrownBy responseAs[ServiceDiscoveryResult]
    }
  }

  it should "handle authorization failures reported by routes" in withRetry {
    val endpoint = new ApiEndpoint(
      resourceProvider = new MockResourceProvider(Set.empty),
      eventCollector = EventCollector.NoOp,
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword),
      userCredentialsManager = MockUserCredentialsManager(),
      serviceDiscoveryProvider = _ => Future.successful(ServiceDiscoveryResult.KeepExisting),
      secretsConfig = testSecretsConfig
    )

    val endpointPort = ports.dequeue()
    endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/v1/users/self"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.Forbidden)
      }
  }

  it should "handle generic failures reported by routes" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val userStore: UserStore = MockUserStore()

    val endpoint = new ApiEndpoint(
      resourceProvider = new MockResourceProvider(Set(userStore.manageSelf())),
      eventCollector = EventCollector.NoOp,
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword),
      userCredentialsManager = MockUserCredentialsManager(),
      serviceDiscoveryProvider = _ => Future.successful(ServiceDiscoveryResult.KeepExisting),
      secretsConfig = testSecretsConfig
    )

    val endpointPort = ports.dequeue()
    endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.PUT,
          uri = s"http://localhost:$endpointPort/v1/users/self/deactivate"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  it should "reject requests with invalid entities" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val userStore: UserStore = MockUserStore()

    val endpoint = new ApiEndpoint(
      resourceProvider = new MockResourceProvider(Set(userStore.manageSelf())),
      eventCollector = EventCollector.NoOp,
      authenticator = new MockUserAuthenticator(testUser.toString, testPassword),
      userCredentialsManager = MockUserCredentialsManager(),
      serviceDiscoveryProvider = _ => Future.successful(ServiceDiscoveryResult.KeepExisting),
      secretsConfig = testSecretsConfig
    )

    val endpointPort = ports.dequeue()
    endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:$endpointPort/v1/users",
          entity = HttpEntity(ContentTypes.`application/json`, "{\"a\":1}")
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Provided data is invalid or malformed"
        )
      }
  }

  private implicit val typedSystem: org.apache.pekko.actor.typed.ActorSystem[Nothing] =
    org.apache.pekko.actor.typed.ActorSystem(
      Behaviors.ignore,
      "ApiEndpointSpec_Typed"
    )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val definitionStore: DatasetDefinitionStore = MockDatasetDefinitionStore()
    lazy val entryStore: DatasetEntryStore = MockDatasetEntryStore()
    lazy val deviceStore: DeviceStore = MockDeviceStore()
    lazy val scheduleStore: ScheduleStore = MockScheduleStore()
    lazy val userStore: UserStore = MockUserStore()
    lazy val serverNodeStore: ServerNodeStore = ServerNodeStore(nodeStore)
    lazy val serverManifestStore: ServerManifestStore = ServerManifestStore(manifestStore)
    lazy val serverReservationStore: ServerReservationStore = ServerReservationStore(reservationStore)
    lazy val serverStagingStore: ServerStagingStore = ServerStagingStore(stagingStore)
    lazy val analyticsStore: AnalyticsEntryStore = MockAnalyticsEntryStore()

    lazy val nodeStore: NodeStore = MockNodeStore()
    lazy val manifestStore: ManifestStore = MockManifestStore()
    lazy val reservationStore: ReservationStore = MockReservationStore()
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
        serverManifestStore.manage(),
        serverManifestStore.view(),
        serverReservationStore.view(),
        serverStagingStore.manage(),
        serverStagingStore.view(),
        analyticsStore.manage(),
        analyticsStore.manageSelf(),
        analyticsStore.view()
      )
    )

    lazy val authenticator: UserAuthenticator = new MockUserAuthenticator(testUser.toString, testPassword)

    lazy val endpoint: ApiEndpoint = new ApiEndpoint(
      resourceProvider = provider,
      eventCollector = EventCollector.NoOp,
      authenticator = authenticator,
      userCredentialsManager = MockUserCredentialsManager(),
      serviceDiscoveryProvider = _ => Future.successful(ServiceDiscoveryResult.KeepExisting),
      secretsConfig = testSecretsConfig
    )
  }

  private val testUser = User.generateId()
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser.toString, password = testPassword)

  private val ports: mutable.Queue[Int] = (21000 to 21100).to(mutable.Queue)
}
