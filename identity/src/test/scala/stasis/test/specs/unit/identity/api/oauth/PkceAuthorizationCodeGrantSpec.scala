package stasis.test.specs.unit.identity.api.oauth

import akka.http.scaladsl.model
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, CacheDirectives}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import play.api.libs.json._
import stasis.identity.api.oauth.PkceAuthorizationCodeGrant
import stasis.identity.api.oauth.PkceAuthorizationCodeGrant._
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, StoredAuthorizationCode}
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.TokenType
import stasis.identity.model.{ChallengeMethod, GrantType, ResponseType}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

class PkceAuthorizationCodeGrantSpec extends RouteTest with OAuthFixtures {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

  "PkceAuthorizationCodeGrant routes" should "validate authorization requests content" in {
    val request = AuthorizationRequest(
      response_type = ResponseType.Code,
      client_id = Client.generateId(),
      redirect_uri = Some("some-uri"),
      scope = Some("some-scope"),
      state = "some-state",
      code_challenge = "some-challenge",
      code_challenge_method = None
    )

    an[IllegalArgumentException] should be thrownBy request.copy(response_type = ResponseType.Token)
    an[IllegalArgumentException] should be thrownBy request.copy(redirect_uri = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(scope = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(state = "")
    an[IllegalArgumentException] should be thrownBy request.copy(code_challenge = "")
  }

  they should "validate access token requests content" in {
    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = AuthorizationCode("some-code"),
      redirect_uri = Some("some-uri"),
      client_id = Client.generateId(),
      code_verifier = "some-verifier"
    )

    an[IllegalArgumentException] should be thrownBy request.copy(grant_type = GrantType.Implicit)
    an[IllegalArgumentException] should be thrownBy request.copy(code = AuthorizationCode(""))
    an[IllegalArgumentException] should be thrownBy request.copy(redirect_uri = Some(""))
    an[IllegalArgumentException] should be thrownBy request.copy(code_verifier = "")
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

  they should "generate authorization codes for valid requests" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new PkceAuthorizationCodeGrant(config, providers)

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
      response_type = ResponseType.Code,
      client_id = client.id,
      redirect_uri = Some(client.redirectUri),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = Generators.generateString(withSize = 16),
      code_challenge = Generators.generateString(withSize = 128),
      code_challenge_method = Some(ChallengeMethod.Plain)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(request).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.Found)
      stores.codes.get(client.id).await match {
        case Some(storedCode) =>
          storedCode.challenge should be(
            Some(StoredAuthorizationCode.Challenge(request.code_challenge, request.code_challenge_method))
          )

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

  they should "not generate authorization codes when invalid redirect URIs are provided" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new PkceAuthorizationCodeGrant(config, providers)

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
      response_type = ResponseType.Code,
      client_id = client.id,
      redirect_uri = Some("some-uri"),
      scope = grant.apiAudienceToScope(Seq(api)),
      state = Generators.generateString(withSize = 16),
      code_challenge = Generators.generateString(withSize = 128),
      code_challenge_method = None
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    Get(request).addCredentials(credentials) ~> grant.authorization() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[String] should be(
        "The request has missing, invalid or mismatching redirection URI and/or client identifier"
      )
      stores.codes.codes.await should be(Map.empty)
    }
  }

  they should "generate access and refresh tokens for valid authorization codes" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new PkceAuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val challenge = StoredAuthorizationCode.Challenge(
      value = Generators.generateString(withSize = 128),
      method = Some(ChallengeMethod.Plain)
    )

    val storedCode = StoredAuthorizationCode(
      code = code,
      owner = owner,
      scope = grant.apiAudienceToScope(Seq(api)),
      challenge = Some(challenge)
    )

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some(client.redirectUri),
      client_id = client.id,
      code_verifier = challenge.value
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(client.id, storedCode).await
    Post(request).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token.exists(_.value.nonEmpty) should be(true)

      val refreshTokenGenerated = stores.tokens.get(client.id).await.nonEmpty
      refreshTokenGenerated should be(true)
    }
  }

  they should "generate only access tokens when refresh tokens are not allowed" in {
    val (stores, secrets, config, providers) = createOAuthFixtures(withRefreshTokens = false)
    val grant = new PkceAuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val challenge = StoredAuthorizationCode.Challenge(
      value = Generators.generateString(withSize = 128),
      method = Some(ChallengeMethod.Plain)
    )

    val storedCode = StoredAuthorizationCode(
      code = code,
      owner = owner,
      scope = grant.apiAudienceToScope(Seq(api)),
      challenge = Some(challenge)
    )

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some(client.redirectUri),
      client_id = client.id,
      code_verifier = challenge.value
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(client.id, storedCode).await
    Post(request).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.OK)
      headers should contain(model.headers.`Cache-Control`(CacheDirectives.`no-store`))

      val actualResponse = responseAs[AccessTokenResponse]
      actualResponse.access_token.value.nonEmpty should be(true)
      actualResponse.token_type should be(TokenType.Bearer)
      actualResponse.expires_in.value should be > 0L
      actualResponse.refresh_token should be(None)

      val refreshTokenGenerated = stores.tokens.get(client.id).await.nonEmpty
      refreshTokenGenerated should be(false)
    }
  }

  they should "not generate access or refresh tokens when invalid redirect URIs are provided" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new PkceAuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val challenge = StoredAuthorizationCode.Challenge(
      value = Generators.generateString(withSize = 128),
      method = Some(ChallengeMethod.Plain)
    )

    val storedCode = StoredAuthorizationCode(
      code = code,
      owner = owner,
      scope = grant.apiAudienceToScope(Seq(api)),
      challenge = Some(challenge)
    )

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some("some-uri"),
      client_id = client.id,
      code_verifier = challenge.value
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(client.id, storedCode).await
    Post(request).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_request"))
    }
  }

  they should "not generate access or refresh tokens when invalid code verifiers are provided" in {
    val (stores, secrets, config, providers) = createOAuthFixtures()
    val grant = new PkceAuthorizationCodeGrant(config, providers)

    val owner = Generators.generateResourceOwner
    val code = Generators.generateAuthorizationCode
    val api = Generators.generateApi

    val rawPassword = "some-password"
    val salt = Generators.generateString(withSize = secrets.client.saltSize)
    val client = Generators.generateClient.copy(
      secret = Secret.derive(rawPassword, salt)(secrets.client),
      salt = salt
    )
    val credentials = BasicHttpCredentials(client.id.toString, rawPassword)

    val challenge = StoredAuthorizationCode.Challenge(
      value = Generators.generateString(withSize = 128),
      method = Some(ChallengeMethod.Plain)
    )

    val storedCode = StoredAuthorizationCode(
      code = code,
      owner = owner,
      scope = grant.apiAudienceToScope(Seq(api)),
      challenge = Some(challenge)
    )

    val request = AccessTokenRequest(
      grant_type = GrantType.AuthorizationCode,
      code = code,
      redirect_uri = Some(client.redirectUri),
      client_id = client.id,
      code_verifier = Generators.generateString(withSize = 128)
    )

    stores.clients.put(client).await
    stores.owners.put(owner).await
    stores.apis.put(api).await
    stores.codes.put(client.id, storedCode).await
    Post(request).addCredentials(credentials) ~> grant.token() ~> check {
      status should be(StatusCodes.BadRequest)
      responseAs[JsObject].fields should contain("error" -> JsString("invalid_grant"))
    }
  }

  import scala.language.implicitConversions

  implicit def authorizationRequestToLocalUri(request: AuthorizationRequest): Uri =
    Uri(
      s"/" +
        s"?response_type=${request.response_type.toString.toLowerCase}" +
        s"&client_id=${request.client_id}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&scope=${request.scope.getOrElse("")}" +
        s"&state=${request.state}" +
        s"&code_challenge=${request.code_challenge}" +
        request.code_challenge_method.map(m => s"&code_challenge_method=$m").getOrElse("")
    )

  implicit def accessTokenRequestToLocalUri(request: AccessTokenRequest): Uri =
    Uri(
      s"/" +
        s"?grant_type=authorization_code" +
        s"&code=${request.code.value}" +
        s"&redirect_uri=${request.redirect_uri.getOrElse("")}" +
        s"&client_id=${request.client_id}" +
        s"&code_verifier=${request.code_verifier}"
    )
}
