package stasis.test.specs.unit.core.security

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.security.JwtNodeCredentialsProvider
import stasis.core.security.exceptions.ProviderFailure
import stasis.test.specs.unit.AsyncUnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class JwtNodeCredentialsProviderSpec extends AsyncUnitSpec {
  "A JwtNodeCredentialsProvider" should "provide node credentials" in {
    val expectedToken = "some-token"
    val (provider, store) = createProvider(token = expectedToken)

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true
    )

    store.put(node).await
    provider
      .provide(node.address)
      .map { credentials =>
        credentials should be(OAuth2BearerToken(expectedToken))
      }
  }

  it should "fail to provide credentials for missing nodes" in {
    val (provider, store) = createProvider(token = "some-token")

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true
    )

    val otherAddress = HttpEndpointAddress("http://some-address:9001")

    store.put(node).await
    provider
      .provide(otherAddress)
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e should be(ProviderFailure(s"Failed to find node with address [$otherAddress]"))
      }
  }

  private def createProvider(token: String): (JwtNodeCredentialsProvider[HttpEndpointAddress], NodeStore) = {
    val storeInit = NodeStore(
      backend = MemoryBackend[Node.Id, Node](name = s"node-store-${java.util.UUID.randomUUID()}"),
      cachingEnabled = false
    )

    val provider = new JwtNodeCredentialsProvider[HttpEndpointAddress](
      nodeStore = storeInit.store.view,
      underlying = (_: String) => Future.successful(token)
    )

    (provider, storeInit.store)
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "JwtNodeCredentialsProviderSpec"
  )

  private implicit val ec: ExecutionContext = system.executionContext
}
