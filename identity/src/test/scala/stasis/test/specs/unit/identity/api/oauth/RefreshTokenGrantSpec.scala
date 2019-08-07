package stasis.test.specs.unit.identity.api.oauth

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, CacheDirectives}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import stasis.identity.api.oauth.RefreshTokenGrant
import stasis.identity.api.oauth.RefreshTokenGrant.{AccessTokenRequest, AccessTokenResponse}
import stasis.identity.model.GrantType
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.{RefreshToken, TokenType}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class RefreshTokenGrantSpec extends RouteTest with OAuthFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "RefreshTokenGrant routes" should "validate access token requests content" in {
    val request = AccessTokenRequest(
      grant_type = GrantType.RefreshToken,
      refresh_token = RefreshToken("some-token"),
      scope = Some("some-scope")
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.ClientCredentials)
    an[IllegalArgumentException] should be thrownBy request.copy(refresh_token = RefreshToken(""))
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
  }

  they should "generate access and refresh tokens for valid refresh tokens" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new RefreshTokenGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val token = Generators.generateRefreshToken

    val request = AccessTokenRequest(
      grant_type = GrantType.RefreshToken,
      refresh_token = token,
      scope = grant.apiAudienceToScope(Seq(api))
    )

    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.tokens.put(client.id, token, owner, request.scope).await
    Post(request).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)
      actualResponse.scope should be(request.scope)

      val newTokenGenerated = stores.tokens.tokens.await.headOption.exists(_._2.token != token)
      newTokenGenerated should be(true)
    }
  }

  they should "generate only access tokens when refresh tokens are not allowed" in {
    val (stores, secrets, config, providers) = createOAuthFixtures(withRefreshTokens = false)
    val grant = new RefreshTokenGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val token = Generators.generateRefreshToken

    val request = AccessTokenRequest(
      grant_type = GrantType.RefreshToken,
      refresh_token = token,
      scope = grant.apiAudienceToScope(Seq(api))
    )

    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.clients.put(client).await
    stores.tokens.put(client.id, token, owner, request.scope).await
    Post(request).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

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
        s"?grant_type=refresh_token" +
        s"&refresh_token=${request.refresh_token.value}" +
        s"&scope=${request.scope.getOrElse("")}"
    )
}
