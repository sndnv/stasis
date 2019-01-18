package stasis.test.specs.unit.security.psk

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import stasis.persistence.backends.memory.MemoryBackend
import stasis.routing.Node
import stasis.security.psk.PreSharedKeyAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec

import scala.util.control.NonFatal

class PreSharedKeyAuthenticatorSpec extends AsyncUnitSpec {

  private implicit val system: ActorSystem[SpawnProtocol] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol.behavior): Behavior[SpawnProtocol],
    "PreSharedKeyAuthenticatorSpec"
  )

  "A PreSharedKeyAuthenticator" should "authenticate nodes with valid secrets" in {
    val backend = MemoryBackend[String, String]("psk-authenticator-store")
    val authenticator = new PreSharedKeyAuthenticator(backend)

    val expectedNode: Node.Id = Node.generateId()
    val nodeSecret: String = "some-secret"

    backend.put(expectedNode.toString, nodeSecret).await

    for {
      actualNode <- authenticator.authenticate(credentials = (expectedNode.toString, nodeSecret))
    } yield {
      actualNode should be(expectedNode)
    }
  }

  it should "refuse authentication attempts with invalid secrets" in {
    val backend = MemoryBackend[String, String]("psk-authenticator-store")
    val authenticator = new PreSharedKeyAuthenticator(backend)

    val expectedNode: Node.Id = Node.generateId()
    val nodeSecret: String = "some-secret"

    backend.put(expectedNode.toString, nodeSecret).await

    authenticator
      .authenticate(credentials = (expectedNode.toString, "invalid-secret"))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Invalid secret supplied for node [$expectedNode]")
      }
  }

  it should "refuse authentication attempts with invalid node IDs" in {
    val backend = MemoryBackend[String, String]("psk-authenticator-store")
    val authenticator = new PreSharedKeyAuthenticator(backend)

    val node = "some-node"
    val nodeSecret: String = "some-secret"

    authenticator
      .authenticate(credentials = (node, nodeSecret))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Invalid node ID encountered: [$node]")
      }
  }

  it should "refuse authentication attempts for missing nodes" in {
    val backend = MemoryBackend[String, String]("psk-authenticator-store")
    val authenticator = new PreSharedKeyAuthenticator(backend)

    val expectedNode: Node.Id = Node.generateId()
    val nodeSecret: String = "some-secret"

    authenticator
      .authenticate(credentials = (expectedNode.toString, nodeSecret))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Node [$expectedNode] was not found")
      }
  }
}
