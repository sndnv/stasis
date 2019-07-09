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
import stasis.identity.model.realms.Realm
import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.manage.ManageFixtures
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class ManageSpec extends RouteTest with ManageFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "Manage routes" should "handle realm management requests" in {
    val providers = createManageProviders()
    val manage = new Manage(providers, config)

    val expectedRealm = Generators.generateRealm

    providers.realmStore.put(expectedRealm).await
    Get(s"/master/realms/${expectedRealm.id}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Realm] should be(expectedRealm)
    }
  }

  they should "handle authorization code management requests" in {
    val providers = createManageProviders()
    val manage = new Manage(providers, config)

    val client = Client.generateId()
    val code = Generators.generateAuthorizationCode
    val owner = Generators.generateResourceOwner

    providers.codeStore.put(client, StoredAuthorizationCode(code, owner, scope = None)).await
    Get(s"/master/codes/$client").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("code" -> JsString(code.value))
    }
  }

  they should "handle refresh token management requests" in {
    val providers = createManageProviders()
    val manage = new Manage(providers, config)

    val client = Client.generateId()
    val token = Generators.generateRefreshToken
    val owner = Generators.generateResourceOwner

    providers.tokenStore.put(client, token, owner, scope = None).await
    Get(s"/master/tokens/$client").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("token" -> JsString(token.value))
    }
  }

  they should "handle API management requests" in {
    val providers = createManageProviders()
    val manage = new Manage(providers, config)

    val realm = Generators.generateRealm
    val expectedApi = Generators.generateApi.copy(realm = realm.id)

    providers.realmStore.put(realm).await
    providers.apiStore.put(expectedApi).await
    Get(s"/${realm.id}/apis/${expectedApi.id}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Api] should be(expectedApi)
    }
  }

  they should "handle client management requests" in {
    val providers = createManageProviders()
    val manage = new Manage(providers, config)

    val realm = Generators.generateRealm
    val expectedClient = Generators.generateClient.copy(realm = realm.id)

    providers.realmStore.put(realm).await
    providers.clientStore.put(expectedClient).await
    Get(s"/${realm.id}/clients/${expectedClient.id}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("id" -> JsString(expectedClient.id.toString))
    }
  }

  they should "handle resource owner management requests" in {
    val providers = createManageProviders()
    val manage = new Manage(providers, config)

    val realm = Generators.generateRealm
    val expectedOwner = Generators.generateResourceOwner.copy(realm = realm.id)

    providers.realmStore.put(realm).await
    providers.ownerStore.put(expectedOwner).await
    Get(s"/${realm.id}/owners/${expectedOwner.username}").addCredentials(credentials) ~> manage.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[JsObject].fields should contain("username" -> JsString(expectedOwner.username))
    }
  }

  private val config = Config(
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
