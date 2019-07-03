package stasis.test.specs.unit.identity.api.oauth.directives

import akka.event.LoggingAdapter
import akka.http.scaladsl.model
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, HttpChallenges, OAuth2BearerToken}
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, Materializer}
import play.api.libs.json._
import stasis.identity.api.Formats._
import stasis.identity.api.oauth.directives.ClientAuthentication
import stasis.identity.authentication.oauth.{ClientAuthenticator, DefaultClientAuthenticator}
import stasis.identity.model.clients.ClientStore
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class ClientAuthenticationSpec extends RouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "A ClientAuthentication directive" should "successfully authenticate clients" in {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val realm = Generators.generateRealm

    val clientPassword = "some-password"
    val salt = Secret.generateSalt()
    val secret = Secret.derive(clientPassword, salt)
    val client = Generators.generateClient.copy(secret = secret, salt = salt)
    val credentials = BasicHttpCredentials(client.id.toString, clientPassword)

    val routes = directive.authenticateClient(realm = realm) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsString] should be(JsString(client.id.toString))
    }
  }

  it should "fail if a client could not be authenticated" in {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val realm = Generators.generateRealm

    val salt = Secret.generateSalt()
    val secret = Secret.derive("some-password", salt)
    val client = Generators.generateClient.copy(secret = secret, salt = salt)
    val credentials = BasicHttpCredentials(client.id.toString, "invalid-password")

    val routes = directive.authenticateClient(realm = realm) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should contain(model.headers.`WWW-Authenticate`(HttpChallenges.basic(realm.id)))
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_client"))
    }
  }

  it should "fail if a client provided unsupported credentials" in {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val realm = Generators.generateRealm

    val salt = Secret.generateSalt()
    val secret = Secret.derive("some-password", salt)
    val client = Generators.generateClient.copy(secret = secret, salt = salt)
    val credentials = OAuth2BearerToken("some-token")

    val routes = directive.authenticateClient(realm = realm) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get().addCredentials(credentials) ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should contain(model.headers.`WWW-Authenticate`(HttpChallenges.basic(realm.id)))
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_client"))
    }
  }

  it should "fail if a client provided no credentials" in {
    val clients = createClientStore()
    val directive = createDirective(clients)

    val realm = Generators.generateRealm
    val client = Generators.generateClient

    val routes = directive.authenticateClient(realm = realm) { extractedClient =>
      Directives.complete(StatusCodes.OK, extractedClient.id.toString)
    }

    clients.put(client).await
    Get() ~> routes ~> check {
      status should be(StatusCodes.Unauthorized)
      headers should contain(model.headers.`WWW-Authenticate`(HttpChallenges.basic(realm.id)))
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_client"))
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
  ) = new ClientAuthentication {
    override implicit protected def mat: Materializer = ActorMaterializer()

    override protected def log: LoggingAdapter = createLogger()

    override protected def clientAuthenticator: ClientAuthenticator = new DefaultClientAuthenticator(
      store = clients.view,
      secretConfig = secretConfig
    )
  }
}
