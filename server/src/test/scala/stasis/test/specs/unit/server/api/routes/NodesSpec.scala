package stasis.test.specs.unit.server.api.routes

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.RequestEntity
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.layers.telemetry.TelemetryContext
import stasis.server.api.routes.Nodes
import stasis.server.api.routes.RoutesContext
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.security.CurrentUser
import stasis.server.security.ResourceProvider
import stasis.shared.api.requests.CreateNode
import stasis.shared.api.requests.CreateNode.CreateLocalNode
import stasis.shared.api.requests.UpdateNode
import stasis.shared.api.requests.UpdateNode.UpdateLocalNode
import stasis.shared.api.responses.CreatedNode
import stasis.shared.api.responses.DeletedNode
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class NodesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  "Nodes routes" should "respond with all nodes" in withRetry {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Node]] should be(Seq(node))
    }
  }

  they should "create new nodes" in withRetry {
    val fixtures = new TestFixtures {}

    val node = Generators.generateLocalNode
    val request = CreateLocalNode(node.storeDescriptor)

    Post("/").withEntity(request) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.nodeStore
        .get(entityAs[CreatedNode].node)
        .map(_.isDefined should be(true))
    }
  }

  they should "respond with existing nodes" in withRetry {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    Get(s"/${node.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Node] should be(node)
    }
  }

  they should "fail if a node is missing" in withRetry {
    val fixtures = new TestFixtures {}

    Get(s"/${Node.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing nodes" in withRetry {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    val request = UpdateLocalNode(storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp"))

    Put(s"/${node.id}").withEntity(request) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.nodeStore.get(node.id).map {
        case Some(updatedNode: Node.Local) => updatedNode.storeDescriptor should be(request.storeDescriptor)
        case other                         => fail(s"Unexpected response received: [$other]")
      }
    }
  }

  they should "fail to update if a node is missing" in withRetry {
    val fixtures = new TestFixtures {}

    val request = UpdateLocalNode(storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp"))

    Put(s"/${Node.generateId()}").withEntity(request) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing nodes" in withRetry {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    Delete(s"/${node.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedNode] should be(DeletedNode(existing = true))
    }
  }

  they should "not delete missing nodes" in withRetry {
    val fixtures = new TestFixtures {}

    Delete(s"/${Node.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedNode] should be(DeletedNode(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "NodesSpec"
  )

  private implicit val untypedSystem: org.apache.pekko.actor.ActorSystem = typedSystem.classicSystem

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private trait TestFixtures {
    lazy val nodeStore: NodeStore = new MockNodeStore()

    lazy val serverNodeStore: ServerNodeStore = ServerNodeStore(nodeStore)

    lazy implicit val provider: ResourceProvider = new MockResourceProvider(
      resources = Set(
        serverNodeStore.view(),
        serverNodeStore.manage()
      )
    )

    lazy implicit val context: RoutesContext = RoutesContext.collect()

    lazy val routes: Route = new Nodes().routes
  }

  private implicit val user: CurrentUser = CurrentUser(User.generateId())

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateNode): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateNode): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
