package stasis.test.specs.unit.identity.api.oauth

import org.apache.pekko.http.scaladsl.model
import org.apache.pekko.http.scaladsl.model.FormData
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.model.headers.CacheDirectives

import stasis.identity.api.oauth.ResourceOwnerPasswordCredentialsGrant
import stasis.identity.api.oauth.ResourceOwnerPasswordCredentialsGrant.AccessTokenRequest
import stasis.identity.api.oauth.ResourceOwnerPasswordCredentialsGrant.AccessTokenResponse
import stasis.identity.model.GrantType
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.TokenType
import stasis.layers
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class ResourceOwnerPasswordCredentialsGrantSpec extends RouteTest with OAuthFixtures {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

  "ResourceOwnerPasswordCredentialsGrant routes" should "validate access token requests content" in withRetry {
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

  they should "generate access and refresh tokens for valid requests (with URL parameters)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ResourceOwnerPasswordCredentialsGrant(config, providers)

    val api = Generators.generateApi

    val clientRawPassword = "some-password"
    val clientSalt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
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
    Post(
      s"/" +
        s"?grant_type=password" +
        s"&username=${request.username}" +
        s"&password=${request.password}" +
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

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "generate access and refresh tokens for valid requests (with form fields)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ResourceOwnerPasswordCredentialsGrant(config, providers)

    val api = Generators.generateApi

    val clientRawPassword = "some-password"
    val clientSalt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
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
    Post(
      "/",
      FormData(
        "grant_type" -> "password",
        "username" -> request.username,
        "password" -> request.password,
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

      val refreshTokenGenerated = stores.tokens.all.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "generate only access tokens when refresh tokens are not allowed" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures(withRefreshTokens = false)
    val grant = new ResourceOwnerPasswordCredentialsGrant(config, providers)

    val api = Generators.generateApi

    val clientRawPassword = "some-password"
    val clientSalt = layers.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(clientRawPassword, clientSalt)(secrets.client),
      salt = clientSalt
    )
    val credentials = BasicHttpCredentials(client.id.toString, clientRawPassword)

    val ownerRawPassword = "some-password"
    val ownerSalt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
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
    Post(
      s"/" +
        s"?grant_type=password" +
        s"&username=${request.username}" +
        s"&password=${request.password}" +
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
