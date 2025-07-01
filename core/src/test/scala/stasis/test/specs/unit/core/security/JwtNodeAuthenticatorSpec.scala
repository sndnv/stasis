package stasis.test.specs.unit.core.security

import java.security.Key
import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

import io.github.sndnv.layers.security.exceptions.AuthenticationFailure
import io.github.sndnv.layers.security.jwt.DefaultJwtAuthenticator
import io.github.sndnv.layers.security.keys.KeyProvider
import io.github.sndnv.layers.security.mocks.MockJwksGenerator
import io.github.sndnv.layers.security.mocks.MockJwtGenerator
import io.github.sndnv.layers.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.jose4j.jwk.JsonWebKey
import org.jose4j.jws.AlgorithmIdentifiers

import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.nodes.NodeStore
import stasis.core.routing.Node
import stasis.core.security.JwtNodeAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.persistence.nodes.MockNodeStore
import stasis.test.specs.unit.core.telemetry.MockTelemetryContext

class JwtNodeAuthenticatorSpec extends AsyncUnitSpec {
  "A JwtNodeAuthenticator" should "successfully authenticate nodes with valid tokens" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, store) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeToken: String = MockJwtGenerator.generateJwt(
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
        telemetry.layers.security.authenticator.authentication should be(1)
      }
  }

  it should "fail to authenticate missing nodes" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, _) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val nodeToken: String = MockJwtGenerator.generateJwt(
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
        telemetry.layers.security.authenticator.authentication should be(1)
      }
  }

  it should "refuse authentication attempts with invalid node IDs" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, store) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    val otherNode = "some-node"
    val nodeToken: String = MockJwtGenerator.generateJwt(
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
        telemetry.layers.security.authenticator.authentication should be(1)
      }
  }

  it should "fail authenticating nodes with unexpected credentials" in {
    implicit val telemetry: MockTelemetryContext = MockTelemetryContext()

    val (authenticator, store) = createAuthenticator()

    val node = Node.Remote.Http(
      id = Node.generateId(),
      address = HttpEndpointAddress("http://some-address:9000"),
      storageAllowed = true,
      created = Instant.now(),
      updated = Instant.now()
    )

    store.put(node).await

    authenticator
      .authenticate(credentials = BasicHttpCredentials(username = "some-user", password = "some-password"))
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover { case NonFatal(e) =>
        e.getMessage should be("Unsupported node credentials provided: [Basic]")
        telemetry.layers.security.authenticator.authentication should be(0)
      }
  }

  private def createAuthenticator()(implicit telemetry: TelemetryContext): (JwtNodeAuthenticator, NodeStore) = {
    val store = MockNodeStore()

    val underlying = new DefaultJwtAuthenticator(
      provider = new KeyProvider {
        override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)

        override def issuer: String = "self"

        override def allowedAlgorithms: Seq[String] =
          Seq(
            AlgorithmIdentifiers.HMAC_SHA256,
            AlgorithmIdentifiers.HMAC_SHA384,
            AlgorithmIdentifiers.HMAC_SHA512,
            AlgorithmIdentifiers.RSA_USING_SHA256,
            AlgorithmIdentifiers.RSA_USING_SHA384,
            AlgorithmIdentifiers.RSA_USING_SHA512,
            AlgorithmIdentifiers.ECDSA_USING_P256_CURVE_AND_SHA256,
            AlgorithmIdentifiers.ECDSA_USING_P384_CURVE_AND_SHA384,
            AlgorithmIdentifiers.ECDSA_USING_P521_CURVE_AND_SHA512
          )
      },
      audience = "self",
      identityClaim = "sub",
      expirationTolerance = 10.seconds
    )

    val authenticator = new JwtNodeAuthenticator(
      nodeStore = store.view,
      underlying = underlying
    )

    (authenticator, store)
  }

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    Behaviors.ignore,
    "JwtNodeAuthenticatorSpec"
  )

  private val jwk: JsonWebKey = MockJwksGenerator.generateRandomRsaKey(Some("rsa-0"))
}
