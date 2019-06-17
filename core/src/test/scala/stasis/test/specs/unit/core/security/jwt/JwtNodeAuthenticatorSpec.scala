package stasis.test.specs.unit.core.security.jwt

import org.jose4j.jwk.JsonWebKey
import stasis.core.routing.Node
import stasis.core.security.jwt.{JwtAuthenticator, JwtNodeAuthenticator}
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.jwt.mocks.{MockJwksGenerators, MockJwtKeyProvider, MockJwtsGenerators}

import scala.concurrent.duration._
import scala.util.control.NonFatal

class JwtNodeAuthenticatorSpec extends AsyncUnitSpec {
  private val jwk: JsonWebKey = MockJwksGenerators.generateRandomRsaKey(Some("rsa-0"))

  "A JwtNodeAuthenticator" should "successfully authenticate nodes with valid tokens" in {
    val underlying = new JwtAuthenticator(
      provider = MockJwtKeyProvider(jwk),
      audience = "self",
      expirationTolerance = 10.seconds
    )

    val authenticator = new JwtNodeAuthenticator(underlying)

    val expectedNode: Node.Id = Node.generateId()
    val nodeToken: String = MockJwtsGenerators.generateJwt(
      issuer = "self",
      audience = "self",
      subject = expectedNode.toString,
      signingKey = jwk
    )

    for {
      actualNode <- authenticator.authenticate(credentials = nodeToken)
    } yield {
      actualNode should be(expectedNode)
    }
  }

  it should s"refuse authentication attempts with invalid node IDs" in {
    val underlying = new JwtAuthenticator(
      provider = MockJwtKeyProvider(jwk),
      audience = "self",
      expirationTolerance = 10.seconds
    )

    val authenticator = new JwtNodeAuthenticator(underlying)

    val expectedNode = "some-node"
    val nodeToken: String = MockJwtsGenerators.generateJwt(
      issuer = "self",
      audience = "self",
      subject = expectedNode,
      signingKey = jwk
    )

    authenticator
      .authenticate(credentials = nodeToken)
      .map { response =>
        fail(s"Received unexpected response from authenticator: [$response]")
      }
      .recover {
        case NonFatal(e) =>
          e.getMessage should be(s"Invalid node ID encountered: [$expectedNode]")
      }
  }
}
