package stasis.test.specs.unit.identity.api.oauth

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, CacheDirectives}
import akka.http.scaladsl.model.{ContentTypes, StatusCodes, Uri}
import stasis.identity.api.oauth.ClientCredentialsGrant
import stasis.identity.api.oauth.ClientCredentialsGrant.{AccessTokenRequest, AccessTokenResponse}
import stasis.identity.model.GrantType
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.TokenType
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class ClientCredentialsGrantSpec extends RouteTest with OAuthFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "ClientCredentialsGrant routes" should "validate access token requests content" in {
    val request = AccessTokenRequest(
      grant_type = GrantType.ClientCredentials,
      scope = Some("some-scope")
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.Implicit)
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
  }

  they should "generate access tokens for valid requests" in {
    val (stores, secrets, providers) = createOAuthFixtures()
    val grant = new ClientCredentialsGrant(providers)

    val realm = Generators.generateRealm

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      realm = realm.id,
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.ClientCredentials,
      scope = grant.clientAudienceToScope(Seq(client))
    )

    stores.clients.put(client).await
    Post(request).addCredentials(credentials) ~> grant.token(realm) ~> check {
      status should be(StatusCodes.OK)

      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.scope should be(request.scope)
    }
  }

  import scala.language.implicitConversions

  implicit def accessTokenRequestToLocalUri(request: AccessTokenRequest): Uri =
    Uri(
      s"/" +
        s"?grant_type=client_credentials" +
        s"&scope=${request.scope.getOrElse("")}"
    )
}
