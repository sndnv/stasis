package stasis.test.specs.unit.identity.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import play.api.libs.json._
import stasis.identity.api.IdentityEndpoint
import stasis.identity.api.manage.setup.Config
import stasis.identity.model.realms.Realm
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.core.security.jwt.mocks.MockJwksGenerators
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.ManageFixtures
import stasis.test.specs.unit.identity.api.oauth.OAuthFixtures
import stasis.test.specs.unit.identity.model.Generators

import scala.collection.mutable
import scala.concurrent.duration._

class IdentityEndpointSpec extends RouteTest with OAuthFixtures with ManageFixtures {
  private val ports: mutable.Queue[Int] = (24000 to 24100).to[mutable.Queue]

  "An IdentityEndpoint" should "provide OAuth routes" in {
    val (stores, _, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val realm = Generators.generateRealm

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(Generators.generateString(withSize = 16))
        )
      ),
      oauthProviders = oauthProviders,
      manageProviders = manageProviders,
      manageConfig = manageConfig
    )

    val responseType = "some-response"

    stores.realms.put(realm).await
    Get(s"/oauth/${realm.id}/authorization?response_type=$responseType") ~> endpoint.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        s"Realm [${realm.id}]: The request includes an invalid response type: [$responseType]"
      )
    }
  }

  it should "provide JWKs routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val (_, _, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val keys = Seq(
      MockJwksGenerators.generateRandomRsaKey(
        keyId = Some(Generators.generateString(withSize = 16))
      )
    )

    val endpoint = new IdentityEndpoint(
      keys = keys,
      oauthProviders = oauthProviders,
      manageProviders = manageProviders,
      manageConfig = manageConfig
    )

    Get("/jwks/jwks.json") ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain(
        "keys" -> JsArray(keys.map(jwk => JsString(jwk.toJson)))
      )
    }
  }

  it should "provide management routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.identity.api.Formats._

    val (_, _, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val realm = Generators.generateRealm

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(Generators.generateString(withSize = 16))
        )
      ),
      oauthProviders = oauthProviders,
      manageProviders = manageProviders,
      manageConfig = manageConfig
    )

    val credentials = OAuth2BearerToken("some-token")

    manageProviders.realmStore.put(realm).await
    Get(s"/manage/master/realms/${realm.id}").addCredentials(credentials) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Realm] should be(realm)
    }
  }

  it should "handle parameter rejections reported by routes" in {
    val endpointPort = ports.dequeue()

    val (stores, _, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val realm = Generators.generateRealm

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(Generators.generateString(withSize = 16))
        )
      ),
      oauthProviders = oauthProviders,
      manageProviders = manageProviders,
      manageConfig = manageConfig
    )

    stores.realms.put(realm).await

    endpoint.start(
      hostname = "localhost",
      port = endpointPort
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/oauth/${realm.id}/authorization?response_type=code"
        )
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response.entity).to[String].await should be(
          "Parameter [client_id] is missing, invalid or malformed"
        )
      }
  }

  it should "handle generic failures reported by routes" in {
    val endpointPort = ports.dequeue()

    val (_, _, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(Generators.generateString(withSize = 16))
        )
      ),
      oauthProviders = oauthProviders,
      manageProviders = manageProviders.copy(realmStore = createFailingRealmStore(failingGet = true)),
      manageConfig = manageConfig
    )

    endpoint.start(
      hostname = "localhost",
      port = endpointPort
    )

    val credentials = OAuth2BearerToken("some-token")

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/manage/master/realms/some-realm"
        ).addCredentials(credentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response.entity).to[String].await should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  private val manageConfig = Config(
    clientSecrets = Secret.ClientConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 20.millis
    ),
    ownerSecrets = Secret.ResourceOwnerConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 20.millis
    )
  )
}
