package stasis.test.specs.unit.identity.api.manage.directives

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.HttpChallenges
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.server.Directives
import org.slf4j.Logger

import stasis.identity.api.manage.directives.UserAuthentication
import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.model.owners.ResourceOwner
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class UserAuthenticationSpec extends RouteTest {
  "A UserAuthentication directive" should "authenticate users with valid bearer tokens" in withRetry {
    val owner = Generators.generateResourceOwner

    val directive = createDirective(
      auth = (_: OAuth2BearerToken) => Future.successful(owner)
    )

    val routes = directive.authenticate() { authenticatedOwner =>
      Directives.complete(StatusCodes.OK, authenticatedOwner.username)
    }

    Get().addCredentials(OAuth2BearerToken("some-token")) ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(owner.username)
    }
  }

  it should "fail to authenticate users with invalid bearer tokens" in withRetry {
    val directive = createDirective(
      auth = (_: OAuth2BearerToken) => Future.failed(new RuntimeException("Test authentication failure"))
    )

    val routes = directive.authenticate() { authenticatedOwner =>
      Directives.complete(StatusCodes.OK, authenticatedOwner.username)
    }

    Get().addCredentials(OAuth2BearerToken("some-token")) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with unsupported credentials" in withRetry {
    val owner = Generators.generateResourceOwner

    val directive = createDirective(
      auth = (_: OAuth2BearerToken) => Future.successful(owner)
    )

    val routes = directive.authenticate() { authenticatedOwner =>
      Directives.complete(StatusCodes.OK, authenticatedOwner.username)
    }

    Get().addCredentials(BasicHttpCredentials("username", "password")) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with no credentials" in withRetry {
    val owner = Generators.generateResourceOwner

    val directive = createDirective(
      auth = (_: OAuth2BearerToken) => Future.successful(owner)
    )

    val routes = directive.authenticate() { authenticatedOwner =>
      Directives.complete(StatusCodes.OK, authenticatedOwner.username)
    }

    Get() ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should be(List(model.headers.`WWW-Authenticate`(HttpChallenges.oAuth2(testRealm))))
    }
  }

  private def createDirective(auth: OAuth2BearerToken => Future[ResourceOwner]) =
    new UserAuthentication {
      override protected def realm: String = testRealm
      override protected def log: Logger = createLogger()
      override protected def authenticator: ResourceOwnerAuthenticator =
        (credentials: OAuth2BearerToken) => auth(credentials)
    }

  private val testRealm: String = "some-realm"
}
