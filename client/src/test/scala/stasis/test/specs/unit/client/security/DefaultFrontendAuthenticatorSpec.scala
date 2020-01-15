package stasis.test.specs.unit.client.security

import java.security.Key

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import org.jose4j.jws.AlgorithmIdentifiers
import stasis.client.security.DefaultFrontendAuthenticator
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.core.security.jwt.JwtAuthenticator
import stasis.core.security.keys.KeyProvider
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.core.security.mocks.{MockJwksGenerators, MockJwtGenerators}

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.control.NonFatal

class DefaultFrontendAuthenticatorSpec extends AsyncUnitSpec { test =>
  "A DefaultFrontendAuthenticator" should "successfully authenticate frontend requests" in {
    val token = generateToken()
    val authenticator = createAuthenticator()

    authenticator
      .authenticate(credentials = OAuth2BearerToken(token))
      .map(_ => succeed)
  }

  it should "fail to authenticate frontend requests with invalid credentials" in {
    val authenticator = createAuthenticator()

    authenticator
      .authenticate(credentials = BasicHttpCredentials(username = "some-user", password = "some-password"))
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover {
        case NonFatal(e: AuthenticationFailure) =>
          e.message should be("Unsupported credentials provided: [Basic]")
      }
  }

  def createAuthenticator(): DefaultFrontendAuthenticator =
    new DefaultFrontendAuthenticator(
      underlying = new JwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

  private def generateToken(): String = MockJwtGenerators.generateJwt(
    issuer = issuer,
    audience = audience,
    subject = subject,
    signatureKey = jwk
  )

  private val subject = "some-subject"
  private val issuer = "some-issuer"
  private val audience = "some-audience"

  private val jwk = MockJwksGenerators.generateRandomRsaKey(keyId = Some("some-key"))

  private val provider = new KeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)
    override def issuer: String = test.issuer
    override def allowedAlgorithms: Seq[String] = Seq(
      AlgorithmIdentifiers.RSA_USING_SHA256,
      AlgorithmIdentifiers.RSA_USING_SHA384,
      AlgorithmIdentifiers.RSA_USING_SHA512
    )
  }
}
