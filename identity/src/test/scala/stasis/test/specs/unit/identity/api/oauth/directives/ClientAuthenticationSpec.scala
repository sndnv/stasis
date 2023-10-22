package stasis.test.specs.unit.identity.api.oauth.directives

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges, OAuth2BearerToken}
import org.apache.pekko.http.scaladsl.server.Directives
import org.slf4j.Logger
import play.api.libs.json._
import stasis.identity.api.oauth.directives.ClientAuthentication
import stasis.identity.authentication.oauth.{ClientAuthenticator, DefaultClientAuthenticator}
import stasis.identity.model.clients.ClientStore
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class ClientAuthenticationSpec extends RouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "A ClientAuthentication directive" should "successfully authenticate clients" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val clientPassword = "some-password"
    val salt = Secret.generateSalt()
    val secret = Secret.derive(clientPassword, salt)
    val client = Generators.generateClient.copy(secret = secret, salt = salt)
    val credentials = BasicHttpCredentials(client.id.toString, clientPassword)

    val routes = directive.authenticateClient() { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsString] should be(Json.toJson(client.id.toString))
    }
  }

  it should "fail if a client could not be authenticated" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val salt = Secret.generateSalt()
    val secret = Secret.derive("some-password", salt)
    val client = Generators.generateClient.copy(secret = secret, salt = salt)
    val credentials = BasicHttpCredentials(client.id.toString, "invalid-password")

    val routes = directive.authenticateClient() { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should contain(model.headers.`WWW-Authenticate`(HttpChallenges.basic(testRealm)))
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_client"))
    }
  }

  it should "fail if a client provided unsupported credentials" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val salt = Secret.generateSalt()
    val secret = Secret.derive("some-password", salt)
    val client = Generators.generateClient.copy(secret = secret, salt = salt)
    val credentials = OAuth2BearerToken("some-token")

    val routes = directive.authenticateClient() { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should contain(model.headers.`WWW-Authenticate`(HttpChallenges.basic(testRealm)))
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_client"))
    }
  }

  it should "fail if a client provided no credentials" in withRetry {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val client = Generators.generateClient

    val routes = directive.authenticateClient() { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should contain(model.headers.`WWW-Authenticate`(HttpChallenges.basic(testRealm)))
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_client"))
    }
  }

  private implicit val secretConfig: Secret.ClientConfig = Secret.ClientConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 20.millis
  )

  private def createDirective(
    clients: ClientStore
  ) =
    new ClientAuthentication {

      override protected def realm: String = testRealm

      override protected def log: Logger = createLogger()

      override protected def clientAuthenticator: ClientAuthenticator =
        new DefaultClientAuthenticator(
          store = clients.view,
          secretConfig = secretConfig
        )
    }

  private val testRealm: String = "some-realm"
}
