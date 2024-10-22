package stasis.test.specs.unit.core.security

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.security.JwtNodeCredentialsProvider
import stasis.layers.security.exceptions.ProviderFailure
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore

class JwtNodeCredentialsProviderSpec extends AsyncUnitSpec {
  "A JwtNodeCredentialsProvider" should "provide node credentials" in {
    val expectedToken = "some-token"
    val (provider, store) = createProvider(token = expectedToken)

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
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
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
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
    val store = MockNodeStore()

    val provider = new JwtNodeCredentialsProvider[HttpEndpointAddress](
      nodeStore = store.view,
      underlying = (_: String) => Future.successful(token)
    )

    (provider, store)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "JwtNodeCredentialsProviderSpec"
  )

  private implicit val ec: ExecutionContext = system.executionContext
}
