package stasis.test.specs.unit.server.api.routes

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.telemetry.TelemetryContext
import stasis.server.api.routes.{Nodes, RoutesContext}
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.security.{CurrentUser, ResourceProvider}
import stasis.shared.api.requests.CreateNode.CreateLocalNode
import stasis.shared.api.requests.UpdateNode.UpdateLocalNode
import stasis.shared.api.requests.{CreateNode, UpdateNode}
import stasis.shared.api.responses.{CreatedNode, DeletedNode}
import stasis.shared.model.users.User
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.Generators
import stasis.test.specs.unit.core.persistence.mocks.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext
import stasis.test.specs.unit.server.security.mocks.MockResourceProvider

class NodesSpec extends AsyncUnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import stasis.core.api.Formats._
  import stasis.shared.api.Formats._

  "Nodes routes" should "respond with all nodes" in {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Node]] should be(Seq(node))
    }
  }

  they should "create new nodes" in {
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

  they should "respond with existing nodes" in {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    Get(s"/${node.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Node] should be(node)
    }
  }

  they should "fail if a node is missing" in {
    val fixtures = new TestFixtures {}

    Get(s"/${Node.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing nodes" in {
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

  they should "fail to update if a node is missing" in {
    val fixtures = new TestFixtures {}

    val request = UpdateLocalNode(storeDescriptor = CrateStore.Descriptor.ForFileBackend(parentDirectory = "/tmp"))

    Put(s"/${Node.generateId()}").withEntity(request) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "delete existing nodes" in {
    val fixtures = new TestFixtures {}
    val node = Generators.generateLocalNode
    fixtures.nodeStore.put(node).await

    Delete(s"/${node.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedNode] should be(DeletedNode(existing = true))
    }
  }

  they should "not delete missing nodes" in {
    val fixtures = new TestFixtures {}

    Delete(s"/${Node.generateId()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[DeletedNode] should be(DeletedNode(existing = false))
    }
  }

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "NodesSpec"
  )

  private implicit val untypedSystem: akka.actor.ActorSystem = typedSystem.classicSystem

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
