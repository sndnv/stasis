package stasis.test.specs.unit.core.security

import java.time.Instant

import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.security.PreSharedKeyNodeAuthenticator
import stasis.layers.persistence.memory.MemoryStore
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class PreSharedKeyNodeAuthenticatorSpec extends AsyncUnitSpec {
  "A PreSharedKeyNodeAuthenticator" should "authenticate nodes with valid secrets" in {

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeSecret: String = "some-secret"

    val (authenticator, store) = createAuthenticator(node.id, nodeSecret)
    store.put(node).await

    authenticator
      .authenticate(credentials = (node.id.toString, nodeSecret))
      .map { actualNode =>
        actualNode should be(node.id)
      }
  }

  it should "fail to authenticate missing nodes" in {
    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeSecret: String = "some-secret"

    val (authenticator, _) = createAuthenticator(node.id, nodeSecret)

    authenticator
      .authenticate(credentials = (node.id.toString, nodeSecret))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Node [${node.id}] not found")
      }
  }

  it should "refuse authentication attempts with invalid secrets" in {
    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeSecret: String = "some-secret"

    val (authenticator, store) = createAuthenticator(node.id, nodeSecret)
    store.put(node).await

    authenticator
      .authenticate(credentials = (node.id.toString, "invalid-secret"))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Invalid secret supplied for node [${node.id}]")
      }
  }

  it should "refuse authentication attempts with invalid node IDs" in {
    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeSecret: String = "some-secret"

    val (authenticator, store) = createAuthenticator(node.id, nodeSecret)
    store.put(node).await

    val otherNode = "some-node"

    authenticator
      .authenticate(credentials = (otherNode, nodeSecret))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Invalid node ID encountered: [$otherNode]")
      }
  }

  it should "refuse authentication attempts for nodes with missing credentials" in {
    val otherNode: Node.Id = Node.generateId()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeSecret: String = "some-secret"

    val (authenticator, store) = createAuthenticator(otherNode, nodeSecret)
    store.put(node).await

    authenticator
      .authenticate(credentials = (node.id.toString, nodeSecret))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Credentials for node [${node.id}] not found")
      }
  }

  private def createAuthenticator(node: Node.Id, nodeSecret: String): (PreSharedKeyNodeAuthenticator, NodeStore) = {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val store = MockNodeStore()

    val backend = MemoryStore[String, String](s"psk-authenticator-store-${java.util.UUID.randomUUID()}")

    val authenticator = new PreSharedKeyNodeAuthenticator(
      nodeStore = store.view,
      backend = backend
    )

    backend.put(node.toString, nodeSecret).await

    (authenticator, store)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "PreSharedKeyNodeAuthenticatorSpec"
  )
}
