package stasis.test.specs.unit.identity.api.manage.directives

import scala.concurrent.Future

import akka.http.scaladsl.model
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives
import akka.stream.{Materializer, SystemMaterializer}
import org.slf4j.Logger
import stasis.identity.api.manage.directives.UserAuthentication
import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.model.owners.ResourceOwner
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class UserAuthenticationSpec extends RouteTest {
  "A UserAuthentication directive" should "authenticate users with valid bearer tokens" in {
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

  it should "fail to authenticate users with invalid bearer tokens" in {
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

  it should "fail to authenticate users with unsupported credentials" in {
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

  it should "fail to authenticate users with no credentials" in {
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
      override implicit protected def mat: Materializer = SystemMaterializer(system).materializer
      override protected def log: Logger = createLogger()
      override protected def authenticator: ResourceOwnerAuthenticator =
        (credentials: OAuth2BearerToken) => auth(credentials)
    }

  private val testRealm: String = "some-realm"
}
