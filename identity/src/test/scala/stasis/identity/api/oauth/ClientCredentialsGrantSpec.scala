package stasis.identity.api.oauth

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives

import stasis.identity.RouteTest
import stasis.identity.api.oauth.ClientCredentialsGrant.AccessTokenRequest
import stasis.identity.api.oauth.ClientCredentialsGrant.AccessTokenResponse
import stasis.identity.model.Generators
import stasis.identity.model.GrantType
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.TokenType
import stasis.layers

class ClientCredentialsGrantSpec extends RouteTest with OAuthFixtures {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "ClientCredentialsGrant routes" should "validate access token requests content" in withRetry {
    val request = AccessTokenRequest(
      grant_type = GrantType.ClientCredentials,
      scope = Some("some-scope")
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.Implicit)
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
  }

  they should "generate access tokens for valid requests (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ClientCredentialsGrant(config, providers)

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.ClientCredentials,
      scope = grant.clientAudienceToScope(Seq(client))
    )

    stores.clients.put(client).await

    Post(
      s"/?grant_type=client_credentials&scope=${request.scope.getOrElse("")}"
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.scope should be(request.scope)
    }
  }

  they should "generate access tokens for valid requests (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ClientCredentialsGrant(config, providers)

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.ClientCredentials,
      scope = grant.clientAudienceToScope(Seq(client))
    )

    stores.clients.put(client).await

    Post(
      "/",
      FormData("grant_type" -> "client_credentials", "scope" -> request.scope.getOrElse(""))
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.scope should be(request.scope)
    }
  }
}
