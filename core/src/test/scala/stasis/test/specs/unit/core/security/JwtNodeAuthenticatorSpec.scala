package stasis.test.specs.unit.core.security

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import org.jose4j.jwk.JsonWebKey
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.security.JwtNodeAuthenticator
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.DefaultJwtAuthenticator
import stasis.core.telemetry.TelemetryContext
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwkProvider, MockJwksGenerators, MockJwtGenerators}
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

import scala.concurrent.duration._
import scala.util.control.NonFatal

class JwtNodeAuthenticatorSpec extends AsyncUnitSpec {
  "A JwtNodeAuthenticator" should "successfully authenticate nodes with valid tokens" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, store) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true
    )

    val nodeToken: String = MockJwtGenerators.generateJwt(
      issuer = "self",
      audience = "self",
      subject = node.id.toString,
      signatureKey = jwk
    )

    store.put(node).await

    authenticator
      .authenticate(credentials = OAuth2BearerToken(nodeToken))
      .map { actualNode =>
        actualNode should be(node.id)
        telemetry.security.authenticator.authentication should be(1)
      }
  }

  it should "fail to authenticate missing nodes" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, _) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true
    )

    val nodeToken: String = MockJwtGenerators.generateJwt(
      issuer = "self",
      audience = "self",
      subject = node.id.toString,
      signatureKey = jwk
    )

    authenticator
      .authenticate(credentials = OAuth2BearerToken(nodeToken))
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e should be(AuthenticationFailure(s"Node [${node.id}] not found"))
        telemetry.security.authenticator.authentication should be(1)
      }
  }

  it should "refuse authentication attempts with invalid node IDs" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, store) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true
    )

    val otherNode = "some-node"
    val nodeToken: String = MockJwtGenerators.generateJwt(
      issuer = "self",
      audience = "self",
      subject = otherNode,
      signatureKey = jwk
    )

    store.put(node).await

    authenticator
      .authenticate(credentials = OAuth2BearerToken(nodeToken))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be(s"Invalid node ID encountered: [$otherNode]")
        telemetry.security.authenticator.authentication should be(1)
      }
  }

  it should "fail authenticating nodes with unexpected credentials" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, store) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true
    )

    store.put(node).await

    authenticator
      .authenticate(credentials = BasicHttpCredentials(username = "some-user", password = "some-password"))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be("Unsupported node credentials provided: [Basic]")
        telemetry.security.authenticator.authentication should be(0)
      }
  }

  private def createAuthenticator()(implicit telemetry: TelemetryContext): (JwtNodeAuthenticator, NodeStore) = {
    val storeInit = NodeStore(
      backend = MemoryBackend[Node.Id, Node](name = s"node-store-${java.util.UUID.randomUUID()}"),
      cachingEnabled = false
    )

    val underlying = new DefaultJwtAuthenticator(
      provider = MockJwkProvider(jwk),
      audience = "self",
      identityClaim = "sub",
      expirationTolerance = 10.seconds
    )

    val authenticator = new JwtNodeAuthenticator(
      nodeStore = storeInit.store.view,
      underlying = underlying
    )

    (authenticator, storeInit.store)
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "JwtNodeAuthenticatorSpec"
  )

  private val jwk: JsonWebKey = MockJwksGenerators.generateRandomRsaKey(Some("rsa-0"))
}
