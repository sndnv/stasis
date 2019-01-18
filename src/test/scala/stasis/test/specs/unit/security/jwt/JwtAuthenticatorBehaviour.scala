package stasis.test.specs.unit.security.jwt

import org.jose4j.jwk.JsonWebKey
import stasis.routing.Node
import stasis.security.jwt.JwtAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.security.jwt.mocks.{MockJwtKeyProvider, MockJwtsGenerators}

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait JwtAuthenticatorBehaviour {
  _: AsyncUnitSpec =>
  def authenticator(withKeyType: String, withJwk: JsonWebKey): Unit = {
    it should s"authenticate nodes with valid tokens ($withKeyType)" in {
      val authenticator = new JwtAuthenticator(
        provider = MockJwtKeyProvider(withJwk),
        audience = "self",
        expirationTolerance = 10.seconds
      )

      val expectedNode: Node.Id = Node.generateId()
      val nodeToken: String = MockJwtsGenerators.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedNode.toString,
        signingKey = withJwk
      )

      for {
        actualNode <- authenticator.authenticate(credentials = nodeToken)
      } yield {
        actualNode should be(expectedNode)
      }
    }

    it should s"refuse authentication attempts with invalid tokens ($withKeyType)" in {
      val authenticator = new JwtAuthenticator(
        provider = MockJwtKeyProvider(withJwk),
        audience = "self",
        expirationTolerance = 10.seconds
      )

      val expectedNode: Node.Id = Node.generateId()
      val nodeToken: String = MockJwtsGenerators.generateJwt(
        issuer = "self",
        audience = "some-audience",
        subject = expectedNode.toString,
        signingKey = withJwk
      )

      authenticator
        .authenticate(credentials = nodeToken)
        .map { response =>
          fail(s"Received unexpected response from authenticator: [$response]")
        }
        .recover {
          case NonFatal(e) =>
            e.getMessage should startWith(s"Failed to authenticate token")
        }
    }

    it should s"refuse authentication attempts with invalid node IDs ($withKeyType)" in {
      val authenticator = new JwtAuthenticator(
        provider = MockJwtKeyProvider(withJwk),
        audience = "self",
        expirationTolerance = 10.seconds
      )

      val expectedNode = "some-node"
      val nodeToken: String = MockJwtsGenerators.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedNode,
        signingKey = withJwk
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
}
