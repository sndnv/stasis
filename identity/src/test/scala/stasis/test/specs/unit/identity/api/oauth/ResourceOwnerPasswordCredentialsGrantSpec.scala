package stasis.test.specs.unit.identity.api.oauth

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, CacheDirectives}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes, Uri}
import stasis.identity.api.oauth.ResourceOwnerPasswordCredentialsGrant
import stasis.identity.api.oauth.ResourceOwnerPasswordCredentialsGrant.{AccessTokenRequest, AccessTokenResponse}
import stasis.identity.model.GrantType
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.TokenType
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class ResourceOwnerPasswordCredentialsGrantSpec extends RouteTest with OAuthFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "ResourceOwnerPasswordCredentialsGrant routes" should "validate access token requests content" in {
    val request = AccessTokenRequest(
      grant_type = GrantType.Password,
      username = "some-username",
      password = "some-password",
      scope = Some("some-scope")
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.ClientCredentials)
    an[IllegalArgumentException] should be thrownBy request.copy(username = "")
    an[IllegalArgumentException] should be thrownBy request.copy(password = "")
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
  }

  they should "generate access and refresh tokens for valid requests" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val grant = new ResourceOwnerPasswordCredentialsGrant(providers)

    val realm = Generators.generateRealm
    val api = Generators.generateApi.copy(realm = realm.id)

    val clientRawPassword = "some-password"
    val clientSalt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      realm = realm.id,
      password = Secret.derive(ownerRawPassword, ownerSalt)(secrets.owner),
      salt = ownerSalt
    )

    val request = AccessTokenRequest(
      grant_type = GrantType.Password,
      username = owner.username,
      password = ownerRawPassword,
      scope = grant.apiAudienceToScope(Seq(api))
    )

    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    Post(request).addCredentials(credentials) ~> grant.token(realm) ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)
      actualResponse.scope should be(request.scope)

      val refreshTokenGenerated = stores.tokens.get(client.id).await.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "generate only access tokens when refresh tokens are not allowed" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val grant = new ResourceOwnerPasswordCredentialsGrant(providers)

    val realm = Generators.generateRealm.copy(refreshTokensAllowed = false)
    val api = Generators.generateApi.copy(realm = realm.id)

    val clientRawPassword = "some-password"
    val clientSalt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      realm = realm.id,
      password = Secret.derive(ownerRawPassword, ownerSalt)(secrets.owner),
      salt = ownerSalt
    )

    val request = AccessTokenRequest(
      grant_type = GrantType.Password,
      username = owner.username,
      password = ownerRawPassword,
      scope = grant.apiAudienceToScope(Seq(api))
    )

    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.owners.put(owner).await
    Post(request).addCredentials(credentials) ~> grant.token(realm) ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Content-Type`(ContentTypes.`application/json`))
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token should be(None)
      actualResponse.scope should be(request.scope)

      stores.tokens.tokens.await should be(Map.empty)
    }
  }

  import scala.language.implicitConversions

  implicit def accessTokenRequestToLocalUri(request: AccessTokenRequest): Uri =
    Uri(
      s"/" +
        s"?grant_type=password" +
        s"&username=${request.username}" +
        s"&password=${request.password}" +
        s"&scope=${request.scope.getOrElse("")}"
    )
}
