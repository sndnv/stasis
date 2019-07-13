package stasis.test.specs.unit.core.security.jwt

import org.jose4j.jwk.JsonWebKey
import stasis.core.security.jwt.JwtAuthenticator
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.jwt.mocks.{MockJwtKeyProvider, MockJwtsGenerators}

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait JwtAuthenticatorBehaviour {
  _: AsyncUnitSpec =>
  def authenticator(withKeyType: String, withJwk: JsonWebKey): Unit = {
    it should s"successfully authenticate valid tokens ($withKeyType)" in {
      val authenticator = new JwtAuthenticator(
        provider = MockJwtKeyProvider(withJwk),
        audience = "self",
        expirationTolerance = 10.seconds
      )

      val expectedSubject = "some-subject"
      val token: String = MockJwtsGenerators.generateJwt(
        issuer = "self",
        audience = "self",
        subject = expectedSubject,
        signatureKey = withJwk
      )

      for {
        claims <- authenticator.authenticate(credentials = token)
      } yield {
        claims.getSubject should be(expectedSubject)
      }
    }

    it should s"refuse authentication attempts with invalid tokens ($withKeyType)" in {
      val authenticator = new JwtAuthenticator(
        provider = MockJwtKeyProvider(withJwk),
        audience = "self",
        expirationTolerance = 10.seconds
      )

      val expectedSubject = "some-subject"
      val token: String = MockJwtsGenerators.generateJwt(
        issuer = "self",
        audience = "some-audience",
        subject = expectedSubject,
        signatureKey = withJwk
      )

      authenticator
        .authenticate(credentials = token)
        .map { response =>
          fail(s"Received unexpected response from authenticator: [$response]")
        }
        .recover {
          case NonFatal(e) =>
            e.getMessage should startWith(s"Failed to authenticate token")
        }
    }
  }
}
