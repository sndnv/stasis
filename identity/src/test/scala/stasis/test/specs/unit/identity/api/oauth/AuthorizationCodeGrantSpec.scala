package stasis.test.specs.unit.identity.api.oauth

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, CacheDirectives}
import akka.http.scaladsl.model.{FormData, StatusCodes, Uri}
import play.api.libs.json._
import stasis.identity.api.oauth.AuthorizationCodeGrant
import stasis.identity.api.oauth.AuthorizationCodeGrant._
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, StoredAuthorizationCode}
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.TokenType
import stasis.identity.model.{GrantType, ResponseType}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class AuthorizationCodeGrantSpec extends RouteTest with OAuthFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "AuthorizationCodeGrant routes" should "validate authorization requests content" in {
    val request = AuthorizationRequest(
      response_type = ResponseType.Code,
      client_id = Client.generateId(),
      redirect_uri = Some("some-uri"),
      scope = Some("some-scope"),
      state = "some-state"
    )

    an[IllegalArgumentException] should be thrownBy request.copy(response_type = ResponseType.Token)
    an[IllegalArgumentException] should be thrownBy request.copy(redirect_uri = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(state = "")
  }

  they should "validate access token requests content" in {
    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = AuthorizationCode("some-code"),
      redirect_uri = Some("some-uri"),
      client_id = Client.generateId()
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.Implicit)
    an[IllegalArgumentException] should be thrownBy request.copy(code = AuthorizationCode(""))
    an[IllegalArgumentException] should be thrownBy request.copy(redirect_uri = Some(""))
  }

  they should "represent authorization responses as URI query values" in {
    val response = AuthorizationResponse(
      code = AuthorizationCode("some-code"),
      state = "some-state",
      scope = Some("some-scope")
    )

    val expectedQuery = Uri.Query(
      Map(
        "code" -> "some-code",
        "state" -> "some-state",
        "scope" -> "some-scope"
      )
    )

    val actualQuery = response.asQuery

    actualQuery should be(expectedQuery)
  }

  they should "convert authorization responses to authorization responses with redirect URIs" in {
    val baseResponse = AuthorizationResponse(
      code = AuthorizationCode("some-code"),
      state = "some-state",
      scope = Some("some-scope")
    )

    val expectedResponse = AuthorizationResponseWithRedirectUri(
      code = AuthorizationCode("some-code"),
      state = "some-state",
      scope = Some("some-scope"),
      redirect_uri = "some-uri"
    )

    val actualResponse = AuthorizationResponseWithRedirectUri(baseResponse, "some-uri")

    actualResponse should be(expectedResponse)
  }

  they should "generate authorization codes for valid requests (redirected; with URL parameters)" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val request = AuthorizationRequest(
      response_type = ResponseType.Code,
      client_id = client.id,
      redirect_uri = Some(client.redirectUri),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = stasis.test.Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(
      s"/" +
        s"?response_type=${request.response_type.toString.toLowerCase}" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}"
    ).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.Found)
      stores.codes.codes.await.headOption match {
        case Some((_, storedCode)) =>
          storedCode.challenge should be(None)

          headers should contain(
            model.headers.Location(
              Uri(
                s"${client.redirectUri}" +
                  s"?code=${storedCode.code.value}" +
                  s"&state=${request.state}" +
                  s"&scope=${request.scope.getOrElse("invalid")}"
              )
            )
          )

        case None =>
          fail("Unexpected response received; no authorization code found")
      }
    }
  }

  they should "generate authorization codes for valid requests (completed)" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val request = AuthorizationRequest(
      response_type = ResponseType.Code,
      client_id = client.id,
      redirect_uri = Some(client.redirectUri),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = stasis.test.Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(
      s"/" +
        s"?response_type=${request.response_type.toString.toLowerCase}" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}" +
        s"&no_redirect=true"
    ).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.OK)
      stores.codes.codes.await.headOption match {
        case Some((AuthorizationCode(code), storedCode)) =>
          storedCode.challenge should be(None)

          val response = responseAs[JsObject]
          response.fields should contain("code" -> Json.toJson(code))
          response.fields should contain("state" -> Json.toJson(request.state))
          response.fields should contain("scope" -> Json.toJson(request.scope.getOrElse("invalid")))

          val redirectUri = response.fields.find(_._1 == "redirect_uri") match {
            case Some((_, uri)) => uri.as[String]
            case None           => fail("Unexpected response received; no redirect URI found")
          }

          redirectUri should startWith(request.redirect_uri.getOrElse("invalid"))
          redirectUri should include(s"code=$code")
          redirectUri should include(s"state=${request.state}")
          redirectUri should include(s"scope=${request.scope.getOrElse("invalid")}")

        case None =>
          fail("Unexpected response received; no authorization code found")
      }
    }
  }

  they should "support multiple concurrent authorization requests" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val api = Generators.generateApi
    val client = Generators.generateClient

    stores.apis.put(api).await
    stores.clients.put(client).await

    val requests = stasis.test.Generators.generateSeq(
      min = 3,
      g = {

        val rawPassword = stasis.test.Generators.generateString(24)
        val salt = stasis.test.Generators.generateString(withSize = secrets.owner.saltSize)
        val owner = Generators.generateResourceOwner.copy(
          password = Secret.derive(rawPassword, salt)(secrets.owner),
          salt = salt
        )
        val credentials = BasicHttpCredentials(owner.username, rawPassword)

        val request = AuthorizationRequest(
          response_type = ResponseType.Code,
          client_id = client.id,
          redirect_uri = Some(client.redirectUri),
          scope = grant.apiAudienceToScope(Seq(api)),
          state = stasis.test.Generators.generateString(withSize = 16)
        )

        stores.owners.put(owner).await

        (credentials, request)
      }
    )

    requests.foreach { case (credentials, request) =>
      Get(
        s"/" +
          s"?response_type=${request.response_type.toString.toLowerCase}" +
          s"&client_id=${request.client_id}" +
          s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
          s"&scope=${request.scope.getOrElse("")}" +
          s"&state=${request.state}"
      ).addCredentials(credentials) ~> grant.authorization() ~> check {
        status should be(StatusCodes.Found)
      }
    }

    val codes = stores.codes.codes.await
    codes.size should be(requests.size)
    codes.values.map(_.client).toSeq.distinct should be(Seq(client.id))
    codes.values.map(_.owner.username).toSeq.sorted should be(requests.map(_._1.username).sorted)
  }

  they should "not generate authorization codes when invalid redirect URIs are provided" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val client = Generators.generateClient
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.owner.saltSize)
    val owner = Generators.generateResourceOwner.copy(
      password = Secret.derive(rawPassword, salt)(secrets.owner),
      salt = salt
    )
    val credentials = BasicHttpCredentials(owner.username, rawPassword)

    val request = AuthorizationRequest(
      response_type = ResponseType.Code,
      client_id = client.id,
      redirect_uri = Some("some-uri"),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = stasis.test.Generators.generateString(withSize = 16)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(
      s"/" +
        s"?response_type=${request.response_type.toString.toLowerCase}" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}"
    ).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_request"))
      stores.codes.codes.await should be(Map.empty)
    }
  }

  they should "generate access and refresh tokens for valid authorization codes (with URL parameters)" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some(client.redirectUri),
      client_id = client.id
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(StoredAuthorizationCode(code, client.id, owner, scope = grant.apiAudienceToScope(Seq(api)))).await
    Post(
      s"/" +
        s"?grant_type=authorization_code" +
        s"&code=${request.code.value}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&client_id=${request.client_id}"
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)

      val refreshTokenGenerated = stores.tokens.tokens.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "generate access and refresh tokens for valid authorization codes (with form fields)" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some(client.redirectUri),
      client_id = client.id
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(StoredAuthorizationCode(code, client.id, owner, scope = grant.apiAudienceToScope(Seq(api)))).await
    Post(
      s"/",
      FormData(
        "grant_type" -> "authorization_code",
        "code" -> request.code.value,
        "redirect_uri" -> request.redirect_uri.getOrElse(""),
        "client_id" -> request.client_id.toString
      )
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)

      val refreshTokenGenerated = stores.tokens.tokens.await.headOption.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "generate only access tokens when refresh tokens are not allowed" in {
    val (stores, secrets, config, providers) = createOAuthFixtures(withRefreshTokens = false)
    val grant = new AuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some(client.redirectUri),
      client_id = client.id
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(StoredAuthorizationCode(code, client.id, owner, scope = grant.apiAudienceToScope(Seq(api)))).await
    Post(
      s"/" +
        s"?grant_type=authorization_code" +
        s"&code=${request.code.value}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&client_id=${request.client_id}"
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token should be(None)

      val refreshTokenGenerated = stores.tokens.tokens.await.headOption.nonEmpty
      refreshTokenGenerated should be(false)
    }
  }

  they should "not generate access or refresh tokens when invalid redirect URIs are provided" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new AuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = stasis.test.Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some("some-uri"),
      client_id = client.id
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(StoredAuthorizationCode(code, client.id, owner, scope = grant.apiAudienceToScope(Seq(api)))).await
    Post(
      s"/" +
        s"?grant_type=authorization_code" +
        s"&code=${request.code.value}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&client_id=${request.client_id}"
    ).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> Json.toJson("invalid_request"))
    }
  }
}
