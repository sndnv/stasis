package stasis.test.specs.unit.client.security

import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import stasis.client.security.DefaultFrontendAuthenticator
import stasis.core.security.exceptions.AuthenticationFailure
import stasis.test.specs.unit.AsyncUnitSpec

import scala.util.control.NonFatal

class DefaultFrontendAuthenticatorSpec extends AsyncUnitSpec { test =>
  "A DefaultFrontendAuthenticator" should "successfully authenticate frontend requests" in {
    val authenticator = new DefaultFrontendAuthenticator(token)

    authenticator
      .authenticate(credentials = OAuth2BearerToken(token))
      .map(_ => succeed)
  }

  it should "fail to authenticate frontend requests with invalid credentials" in {
    val authenticator = new DefaultFrontendAuthenticator(token)

    authenticator
      .authenticate(credentials = OAuth2BearerToken("other-token"))
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover { case NonFatal(e: AuthenticationFailure) =>
        e.message should be("Invalid credentials provided")
      }
  }

  it should "fail to authenticate frontend requests with missing credentials" in {
    val authenticator = new DefaultFrontendAuthenticator(token)

    authenticator
      .authenticate(credentials = BasicHttpCredentials(username = "some-user", password = "some-password"))
      .map { result =>
        fail(s"Received unexpected result: [$result]")
      }
      .recover { case NonFatal(e: AuthenticationFailure) =>
        e.message should be("Unsupported credentials provided: [Basic]")
      }
  }

  it should "support generating authentication tokens" in {
    val expectedTokenSize = 12
    val token = DefaultFrontendAuthenticator.generateToken(withSize = expectedTokenSize)
    token should have length expectedTokenSize.toLong
  }

  private val token: String = "test-token"
}
