package stasis.test.specs.unit.identity.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import play.api.libs.json._
import stasis.identity.api.Formats._
import stasis.identity.api.Manage
import stasis.identity.api.manage.setup.Config
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.ManageFixtures
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class ManageSpec extends RouteTest with ManageFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Manage routes" should "handle authorization code management requests" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageCodes))
    val manage = new Manage(providers, config)

    val client = Client.generateId()
    val code = Generators.generateAuthorizationCode
    val owner = Generators.generateResourceOwner

    providers.codeStore.put(StoredAuthorizationCode(code, client, owner, scope = None)).await
    Get(s"/codes/${code.value}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("code" -> Json.toJson(code.value))
    }
  }

  they should "handle refresh token management requests" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageTokens))
    val manage = new Manage(providers, config)

    val client = Client.generateId()
    val token = Generators.generateRefreshToken
    val owner = Generators.generateResourceOwner

    providers.tokenStore.put(client, token, owner, scope = None).await
    Get(s"/tokens/${token.value}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("token" -> Json.toJson(token.value))
    }
  }

  they should "handle API management requests" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageApis))
    val manage = new Manage(providers, config)

    val expectedApi = Generators.generateApi

    providers.apiStore.put(expectedApi).await
    Get(s"/apis/${expectedApi.id}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Api] should be(expectedApi)
    }
  }

  they should "handle client management requests" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageClients))
    val manage = new Manage(providers, config)

    val expectedClient = Generators.generateClient

    providers.clientStore.put(expectedClient).await
    Get(s"/clients/${expectedClient.id}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("id" -> Json.toJson(expectedClient.id.toString))
    }
  }

  they should "handle resource owner management requests" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageOwners))
    val manage = new Manage(providers, config)

    val expectedOwner = Generators.generateResourceOwner

    providers.ownerStore.put(expectedOwner).await
    Get(s"/owners/${expectedOwner.username}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("username" -> Json.toJson(expectedOwner.username))
    }
  }

  they should "reject requests when users fail authentication" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageApis))
    val manage = new Manage(providers, config)

    val expectedApi = Generators.generateApi

    providers.apiStore.put(expectedApi).await
    Get(s"/apis/${expectedApi.id}") ~> manage.routes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  they should "reject requests when users fail authorization" in withRetry {
    val providers = createManageProviders(withOwnerScopes = Seq(Manage.Scopes.ManageClients))
    val manage = new Manage(providers, config)

    val expectedApi = Generators.generateApi

    providers.apiStore.put(expectedApi).await
    Get(s"/apis/${expectedApi.id}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.Forbidden)
    }
  }

  private val config = Config(
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

  private val credentials = OAuth2BearerToken("some-token")
}
