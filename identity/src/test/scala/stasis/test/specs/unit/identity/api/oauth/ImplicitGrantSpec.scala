package stasis.test.specs.unit.identity.api.oauth

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.Uri
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import play.api.libs.json._

import stasis.identity.api.oauth.ImplicitGrant
import stasis.identity.api.oauth.ImplicitGrant._
import stasis.identity.model.ResponseType
import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.AccessToken
import stasis.identity.model.tokens.TokenType
import stasis.layers
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class ImplicitGrantSpec extends RouteTest with OAuthFixtures {
  "ImplicitGrant routes" should "validate authorization requests content" in withRetry {
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

  they should "represent access token responses as URI query values" in withRetry {
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

  they should "convert authorization responses to authorization responses with redirect URIs" in withRetry {
    val baseResponse = AccessTokenResponse(
      access_token = AccessToken("some-token"),
      token_type = TokenType.Bearer,
      expires_in = Seconds(42),
      state = "some-state",
      scope = Some("some-scope")
    )

    val expectedResponse = AccessTokenResponseWithRedirectUri(
      access_token = AccessToken("some-token"),
      token_type = TokenType.Bearer,
      expires_in = Seconds(42),
      state = "some-state",
      scope = Some("some-scope"),
      redirect_uri = "some-uri"
    )

    val actualResponse = AccessTokenResponseWithRedirectUri(baseResponse, "some-uri")

    actualResponse should be(expectedResponse)
  }

  they should "generate access tokens for valid requests (redirected)" in withRetry {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ImplicitGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
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
      state = layers.Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(
      s"/" +
        s"?response_type=token" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}"
    ).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.Found)

      headers.find(_.is("location")) match {
        case Some(header) =>
          val headerValue = header.value()
          headerValue.contains("access_token") should be(true)
          headerValue.contains("token_type=bearer") should be(true)
          headerValue.contains("expires_in=30") should be(true)
          headerValue.contains(s"state=${request.state}") should be(true)
          headerValue.contains(s"scope=${request.scope.getOrElse("invalid")}") should be(true)

        case None =>
          fail(s"Unexpected response received; no location found in headers [$headers]")
      }
    }
  }

  they should "generate access tokens for valid requests (completed)" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ImplicitGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
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
      state = layers.Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(
      s"/" +
        s"?response_type=token" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}" +
        s"&no_redirect=true"
    ).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.OK)

      val response = responseAs[JsObject]
      response.fields.exists(_._1 == "access_token") should be(true)
      response.fields should contain("state" -> Json.toJson(request.state))
      response.fields should contain("scope" -> Json.toJson(request.scope.getOrElse("invalid")))

      val redirectUri = response.fields.find(_._1 == "redirect_uri") match {
        case Some((_, uri)) => uri.as[String]
        case None           => fail("Unexpected response received; no redirect URI found")
      }

      redirectUri should startWith(request.redirect_uri.getOrElse("invalid"))
      redirectUri should include(s"access_token=")
      redirectUri should include(s"state=${request.state}")
      redirectUri should include(s"scope=${request.scope.getOrElse("invalid")}")
    }
  }

  they should "not generate access tokens when invalid redirect URIs are provided" in withRetry {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new ImplicitGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = layers.Generators.generateString(withSize = secrets.owner.saltSize)
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
      state = layers.Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(
      s"/" +
        s"?response_type=token" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}"
    ).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_request"))
    }
  }
}
