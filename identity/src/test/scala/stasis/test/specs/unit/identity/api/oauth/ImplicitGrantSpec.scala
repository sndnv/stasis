package stasis.test.specs.unit.identity.api.oauth

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{StatusCodes, Uri}
import stasis.identity.api.oauth.ImplicitGrant
import stasis.identity.api.oauth.ImplicitGrant.{AccessTokenResponse, AuthorizationRequest}
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.{AccessToken, TokenType}
import stasis.identity.model.{ResponseType, Seconds}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class ImplicitGrantSpec extends RouteTest with OAuthFixtures {
  "ImplicitGrant routes" should "validate authorization requests content" in {
    val request = AuthorizationRequest(
      response_type = ResponseType.Token,
      client_id = Client.generateId(),
      redirect_uri = Some("some-uri"),
      scope = Some("some-scope"),
      state = "some-state"
    )

    an[IllegalArgumentException] should be thrownBy request.copy(response_type = ResponseType.Code)
    an[IllegalArgumentException] should be thrownBy request.copy(redirect_uri = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(state = "")
  }

  they should "represent access token responses as URI query values" in {
    val response = AccessTokenResponse(
      access_token = AccessToken("some-token"),
      token_type = TokenType.Bearer,
      expires_in = Seconds(42),
      state = "some-state",
      scope = Some("some-scope")
    )

    val expectedQuery = Uri.Query(
      Map(
        "access_token" -> "some-token",
        "token_type" -> "bearer",
        "expires_in" -> "42",
        "state" -> "some-state",
        "scope" -> "some-scope"
      )
    )

    val actualQuery = response.asQuery

    actualQuery should be(expectedQuery)
  }

  they should "generate access tokens for valid requests" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ImplicitGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val request = AuthorizationRequest(
      response_type = ResponseType.Token,
      client_id = client.id,
      redirect_uri = Some(client.redirectUri),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(request).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.Found)

      headers.find(_.is("location")) match {
        case Some(header) =>
          val headerValue = header.value()
          headerValue.contains("access_token") should be(true)
          headerValue.contains("token_type=bearer") should be(true)
          headerValue.contains(s"expires_in=${client.tokenExpiration.value}") should be(true)
          headerValue.contains(s"state=${request.state}") should be(true)
          headerValue.contains(s"scope=${request.scope.getOrElse("invalid")}") should be(true)

        case None =>
          fail(s"Unexpected response received; no location found in headers [$headers]")
      }
    }
  }

  they should "not generate access tokens when invalid redirect URIs are provided" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ImplicitGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val request = AuthorizationRequest(
      response_type = ResponseType.Token,
      client_id = client.id,
      redirect_uri = Some("some-uri"),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(request).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        "The request has missing, invalid or mismatching redirection URI and/or client identifier"
      )
    }
  }

  import scala.language.implicitConversions

  implicit def accessTokenRequestToLocalUri(request: AuthorizationRequest): Uri =
    Uri(
      s"/" +
        s"?response_type=token" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}"
    )
}
