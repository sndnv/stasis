package stasis.test.specs.unit.identity.api

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.unmarshalling.Unmarshal
import play.api.libs.json._
import stasis.core.api.MessageResponse
import stasis.identity.api.manage.setup.Config
import stasis.identity.api.{IdentityEndpoint, Manage}
import stasis.identity.model.apis.Api
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.core.security.mocks.MockJwksGenerators
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.ManageFixtures
import stasis.test.specs.unit.identity.api.oauth.OAuthFixtures
import stasis.test.specs.unit.identity.model.Generators

import scala.collection.mutable
import scala.concurrent.duration._

class IdentityEndpointSpec extends RouteTest with OAuthFixtures with ManageFixtures {
  "An IdentityEndpoint" should "provide OAuth routes" in {
    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(stasis.test.Generators.generateString(withSize = 16))
        )
      ),
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )

    val responseType = "some-response"

    Get(s"/oauth/authorization?response_type=$responseType") ~> endpoint.routes ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(s"The request includes an invalid response type: [$responseType]")
    }
  }

  it should "provide JWKs routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val keys = Seq(
      MockJwksGenerators.generateRandomRsaKey(
        keyId = Some(stasis.test.Generators.generateString(withSize = 16))
      )
    )

    val endpoint = new IdentityEndpoint(
      keys = keys,
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )

    Get("/jwks/jwks.json") ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain(
        "keys" -> Json.toJson(keys.map(jwk => Json.parse(jwk.toJson)))
      )
    }
  }

  it should "provide management routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.identity.api.Formats._

    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageApis))

    val api = Generators.generateApi

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(stasis.test.Generators.generateString(withSize = 16))
        )
      ),
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )

    val credentials = OAuth2BearerToken("some-token")

    manageProviders.apiStore.put(api).await
    Get(s"/manage/apis/${api.id}").addCredentials(credentials) ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Api] should be(api)
    }
  }

  it should "handle parameter rejections reported by routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

    val endpointPort = ports.dequeue()

    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders()

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(stasis.test.Generators.generateString(withSize = 16))
        )
      ),
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )

    endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/oauth/authorization?response_type=code"
        )
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response).to[MessageResponse].await.message should be(
          "Parameter [client_id] is missing, invalid or malformed"
        )
      }
  }

  it should "handle generic failures reported by routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

    val endpointPort = ports.dequeue()

    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageApis))

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(stasis.test.Generators.generateString(withSize = 16))
        )
      ),
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders.copy(apiStore = createFailingApiStore(failingGet = true))
    )

    endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    val credentials = OAuth2BearerToken("some-token")

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/manage/apis/some-api"
        ).addCredentials(credentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  it should "reject requests with invalid entities" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import stasis.core.api.Formats.messageResponseFormat

    val endpointPort = ports.dequeue()

    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()
    val manageProviders = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageApis))

    val endpoint = new IdentityEndpoint(
      keys = Seq(
        MockJwksGenerators.generateRandomRsaKey(
          keyId = Some(stasis.test.Generators.generateString(withSize = 16))
        )
      ),
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = manageProviders
    )

    endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    val credentials = OAuth2BearerToken("some-token")

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:$endpointPort/manage/apis",
          entity = HttpEntity(ContentTypes.`application/json`, "{\"a\":1}")
        ).addCredentials(credentials)
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Provided data is invalid or malformed"
        )
      }
  }

  it should "provide a health-check route" in {
    val (_, _, oauthConfig, oauthProviders) = createOAuthFixtures()

    val endpoint = new IdentityEndpoint(
      keys = Seq.empty,
      oauthConfig = oauthConfig,
      oauthProviders = oauthProviders,
      manageConfig = manageConfig,
      manageProviders = createManageProviders()
    )

    Get("/service/health") ~> endpoint.routes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  private val manageConfig = Config(
    realm = "test-realm",
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

  private val ports: mutable.Queue[Int] = (24000 to 24100).to(mutable.Queue)
}
