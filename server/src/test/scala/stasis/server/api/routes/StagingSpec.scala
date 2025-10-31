package stasis.server.api.routes

import java.time.Instant
import java.util.UUID

import scala.concurrent.Future
import scala.concurrent.duration._

import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import play.api.libs.json.JsArray

import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.staging.StagingStore
import stasis.core.routing.Node
import stasis.core.routing.NodeProxy
import stasis.server.events.mocks.MockEventCollector
import stasis.server.persistence.staging.ServerStagingStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.server.security.mocks.MockResourceProvider
import stasis.shared.api.responses.DeletedPendingDestaging
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.networking.mocks.MockGrpcEndpointClient
import stasis.test.specs.unit.core.networking.mocks.MockHttpEndpointClient
import stasis.test.specs.unit.core.persistence.crates.MockCrateStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class StagingSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.shared.api.Formats._

  "Staging routes" should "respond with all pending destaging operations" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.stagingStore
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = proxy
      )
      .await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsArray].value.map(_ \ "crate").map(_.as[UUID]) should be(Seq(testManifest.crate))

      fixtures.eventCollector.events should be(empty)
    }
  }

  they should "drop pending destaging operations" in withRetry {
    val fixtures = new TestFixtures {}

    fixtures.stagingStore
      .stage(
        manifest = testManifest,
        destinations = destinations,
        content = Source.single(testContent),
        viaProxy = proxy
      )
      .await

    Delete(s"/${testManifest.crate}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedPendingDestaging] should be(DeletedPendingDestaging(existing = true))

      fixtures.eventCollector.events should be(empty)
    }
  }

  they should "not drop missing destaging operations" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${testManifest.crate}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedPendingDestaging] should be(DeletedPendingDestaging(existing = false))

      fixtures.eventCollector.events should be(empty)
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "StagingSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val stagingStore: StagingStore = new StagingStore(
      crateStore = new MockCrateStore(),
      destagingDelay = 1.second
    )

    lazy val serverStagingStore: ServerStagingStore = ServerStagingStore(stagingStore)

    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        serverStagingStore.view(),
        serverStagingStore.manage()
      )
    )

    lazy implicit val eventCollector: MockEventCollector = MockEventCollector()

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Staging().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  private val testContent = ByteString("some value")

  private val testManifest = Manifest(
    crate = Crate.generateId(),
    size = testContent.size.toLong,
    copies = 4,
    source = Node.generateId(),
    origin = Node.generateId()
  )

  private val destinations: Map[Node, Int] = Map(
    Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost:8000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    ) -> 1,
    Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("localhost:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    ) -> 1
  )

  private val proxy: NodeProxy = new NodeProxy(
    httpClient = new MockHttpEndpointClient(),
    grpcClient = new MockGrpcEndpointClient()
  ) {
    override protected def crateStore(id: Node.Id, storeDescriptor: CrateStore.Descriptor): Future[CrateStore] =
      Future.failed(new IllegalStateException("Operation not supported"))
  }
}
