package stasis.identity.api.oauth.directives

import scala.concurrent.duration._

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.server.Directives
import org.slf4j.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import stasis.identity.RouteTest
import stasis.identity.authentication.oauth.DefaultResourceOwnerAuthenticator
import stasis.identity.authentication.oauth.ResourceOwnerAuthenticator
import stasis.identity.model.Generators
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.owners.ResourceOwnerStore

class ResourceOwnerAuthenticationSpec extends RouteTest {
  "A ResourceOwnerAuthentication directive" should "authenticate resource owners with provided credentials" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val ownerPassword = "some-password"
    val salt = Secret.generateSalt()
    val password = Secret.derive(ownerPassword, salt)
    val owner = Generators.generateResourceOwner.copy(password = password, salt = salt)

    val routes = directive.authenticateResourceOwner(owner.username, ownerPassword) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(owner.username)
    }
  }

  it should "fail to authenticate resource owners with invalid provided credentials" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val salt = Secret.generateSalt()
    val password = Secret.derive(rawSecret = "some-password", salt)
    val owner = Generators.generateResourceOwner.copy(password = password, salt = salt)

    val routes = directive.authenticateResourceOwner(owner.username, "other-password") { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "authenticate resource owners with extracted credentials (redirected)" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val ownerPassword = "some-password"
    val salt = Secret.generateSalt()
    val password = Secret.derive(ownerPassword, salt)
    val owner = Generators.generateResourceOwner.copy(password = password, salt = salt)
    val credentials = BasicHttpCredentials(owner.username, ownerPassword)
    val redirectUri = Uri("http://example.com")
    val state = "some-state"

    val routes = directive.authenticateResourceOwner(redirectUri, state, noRedirect = false) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(owner.username)
    }
  }

  it should "authenticate resource owners with extracted credentials (rejected)" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val ownerPassword = "some-password"
    val salt = Secret.generateSalt()
    val password = Secret.derive(ownerPassword, salt)
    val owner = Generators.generateResourceOwner.copy(password = password, salt = salt)
    val credentials = BasicHttpCredentials(owner.username, ownerPassword)
    val redirectUri = Uri("http://example.com")
    val state = "some-state"

    val routes = directive.authenticateResourceOwner(redirectUri, state, noRedirect = true) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get("/?no_redirect=true").addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should be(owner.username)
    }
  }

  it should "fail to authenticate resource owners with invalid extracted credentials (redirected)" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val salt = Secret.generateSalt()
    val password = Secret.derive(rawSecret = "some-password", salt)
    val owner = Generators.generateResourceOwner.copy(password = password, salt = salt)
    val credentials = BasicHttpCredentials(owner.username, "other-password")
    val redirectUri = Uri("http://example.com")
    val state = "some-state"

    val routes = directive.authenticateResourceOwner(redirectUri, state, noRedirect = false) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Found)
      headers should contain(
        model.headers.Location(redirectUri.withQuery(AuthorizationError.AccessDenied(withState = state).asQuery))
      )
    }
  }

  it should "fail to authenticate resource owners with invalid extracted credentials (rejected)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val salt = Secret.generateSalt()
    val password = Secret.derive(rawSecret = "some-password", salt)
    val owner = Generators.generateResourceOwner.copy(password = password, salt = salt)
    val credentials = BasicHttpCredentials(owner.username, "other-password")
    val redirectUri = Uri("http://example.com")
    val state = "some-state"

    val routes = directive.authenticateResourceOwner(redirectUri, state, noRedirect = true) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get("/?no_redirect=true").addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      val response = responseAs[JsObject]
      response.fields should contain("error" -> Json.toJson("access_denied"))
      response.fields should contain("state" -> Json.toJson(state))
    }
  }

  it should "fail if a resource owner provided unsupported credentials" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val owner = Generators.generateResourceOwner
    val credentials = OAuth2BearerToken("some-token")
    val redirectUri = Uri("http://example.com")
    val state = "some-state"

    val routes = directive.authenticateResourceOwner(redirectUri, state, noRedirect = false) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Found)
      headers should contain(
        model.headers.Location(redirectUri.withQuery(AuthorizationError.AccessDenied(withState = state).asQuery))
      )
    }
  }

  it should "fail if a resource owner provided no credentials" in withRetry {
    val owners = createOwnerStore()
    val directive = createDirective(owners)

    val owner = Generators.generateResourceOwner
    val redirectUri = Uri("http://example.com")
    val state = "some-state"

    val routes = directive.authenticateResourceOwner(redirectUri, state, noRedirect = false) { extractedOwner =>
      Directives.complete(StatusCodes.OK, extractedOwner.username)
    }

    owners.put(owner).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.Found)
      headers should contain(
        model.headers.Location(redirectUri.withQuery(AuthorizationError.AccessDenied(withState = state).asQuery))
      )
    }
  }

  private implicit val secretConfig: Secret.ResourceOwnerConfig = Secret.ResourceOwnerConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 20.millis
  )

  private def createDirective(
    owners: ResourceOwnerStore
  ) =
    new ResourceOwnerAuthentication {
      override protected def log: Logger = createLogger()
      override protected def resourceOwnerAuthenticator: ResourceOwnerAuthenticator =
        new DefaultResourceOwnerAuthenticator(owners.view, secretConfig)
    }
}
