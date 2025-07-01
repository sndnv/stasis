package stasis.identity.api.oauth

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives

import stasis.identity.RouteTest
import stasis.identity.api.oauth.RefreshTokenGrant.AccessTokenRequest
import stasis.identity.api.oauth.RefreshTokenGrant.AccessTokenResponse
import stasis.identity.model.Generators
import stasis.identity.model.GrantType
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.TokenType
import io.github.sndnv.layers

class RefreshTokenGrantSpec extends RouteTest with OAuthFixtures {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "RefreshTokenGrant routes" should "validate access token requests content" in withRetry {
    val request = AccessTokenRequest(
      grant_type = GrantType.RefreshToken,
      refresh_token = RefreshToken("some-token"),
      scope = Some("some-scope")
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.ClientCredentials)
    an[IllegalArgumentException] should be thrownBy request.copy(refresh_token = RefreshToken(""))
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
  }

  they should "generate access and refresh tokens for valid refresh tokens (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new RefreshTokenGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.testing.Generators.generateString(withSize = secrets.client.saltSize)
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
    Post(
      s"/" +
        s"?grant_type=refresh_token" +
        s"&refresh_token=${request.refresh_token.value}" +
        s"&scope=${request.scope.getOrElse("")}"
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)
      actualResponse.scope should be(request.scope)

      val newTokenGenerated = stores.tokens.all.await.headOption.exists(_.token != token)
      newTokenGenerated should be(true)
    }
  }

  they should "generate access and refresh tokens for valid refresh tokens (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new RefreshTokenGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.testing.Generators.generateString(withSize = secrets.client.saltSize)
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
    Post(
      "/",
      FormData(
        "grant_type" -> "refresh_token",
        "refresh_token" -> request.refresh_token.value,
        "scope" -> request.scope.getOrElse("")
      )
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)
      actualResponse.scope should be(request.scope)

      val newTokenGenerated = stores.tokens.all.await.headOption.exists(_.token != token)
      newTokenGenerated should be(true)
    }
  }

  they should "generate only access tokens when refresh tokens are not allowed" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures(withRefreshTokens = false)
    val grant = new RefreshTokenGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.testing.Generators.generateString(withSize = secrets.client.saltSize)
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
    Post(
      s"/" +
        s"?grant_type=refresh_token" +
        s"&refresh_token=${request.refresh_token.value}" +
        s"&scope=${request.scope.getOrElse("")}"
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token should be(None)
      actualResponse.scope should be(request.scope)

      stores.tokens.all.await should be(Seq.empty)
    }
  }
}
