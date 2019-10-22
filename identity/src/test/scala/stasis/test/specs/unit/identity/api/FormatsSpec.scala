package stasis.test.specs.unit.identity.api

import java.time.Instant

import play.api.libs.json._
import stasis.identity.api.Formats._
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.errors.{AuthorizationError, TokenError}
import stasis.identity.model.tokens.{AccessToken, RefreshToken, StoredRefreshToken, TokenType}
import stasis.identity.model.{ChallengeMethod, GrantType, ResponseType, Seconds}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class FormatsSpec extends UnitSpec {
  "Formats" should "convert authorization errors to JSON" in {
    val error = AuthorizationError.InvalidScope(withState = stasis.test.Generators.generateString(withSize = 16))
    val json = authorizationErrorWrites.writes(error).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("error" -> JsString(error.error))
    parsedFields should contain("error_description" -> JsString(error.error_description))
    parsedFields should contain("state" -> JsString(error.state))
  }

  they should "convert token errors to JSON" in {
    val error = TokenError.InvalidScope
    val json = tokenErrorWrites.writes(error).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("error" -> JsString(error.error))
    parsedFields should contain("error_description" -> JsString(error.error_description))
  }

  they should "convert token types to/from JSON" in {
    val json = "\"bearer\""
    tokenTypeFormat.writes(TokenType.Bearer).toString should be(json)
    tokenTypeFormat.reads(Json.parse(json)).asOpt should be(Some(TokenType.Bearer))
  }

  they should "convert grant types to/from JSON" in {
    val grants = Map(
      GrantType.AuthorizationCode -> "\"authorization_code\"",
      GrantType.ClientCredentials -> "\"client_credentials\"",
      GrantType.Implicit -> "\"implicit\"",
      GrantType.RefreshToken -> "\"refresh_token\"",
      GrantType.Password -> "\"password\""
    )

    grants.foreach {
      case (grant, json) =>
        grantTypeFormat.writes(grant).toString should be(json)
        grantTypeFormat.reads(Json.parse(json)).asOpt should be(Some(grant))
    }
  }

  they should "convert challenge methods t/from JSON" in {
    val challenges = Map(
      ChallengeMethod.Plain -> "\"plain\"",
      ChallengeMethod.S256 -> "\"s256\""
    )

    challenges.foreach {
      case (challenge, json) =>
        challengeMethodFormat.writes(challenge).toString should be(json)
        challengeMethodFormat.reads(Json.parse(json)).asOpt should be(Some(challenge))
    }
  }

  they should "convert response types to/from JSON" in {
    val responses = Map(
      ResponseType.Code -> "\"code\"",
      ResponseType.Token -> "\"token\""
    )

    responses.foreach {
      case (response, json) =>
        responseTypeFormat.writes(response).toString should be(json)
        responseTypeFormat.reads(Json.parse(json)).asOpt should be(Some(response))
    }
  }

  they should "convert access tokens to/from JSON" in {
    val token = AccessToken(value = stasis.test.Generators.generateString(withSize = 16))
    val json = s"""\"${token.value}\""""

    accessTokenFormat.writes(token).toString should be(json)
    accessTokenFormat.reads(Json.parse(json)).asOpt should be(Some(token))
  }

  they should "convert refresh tokens to/from JSON" in {
    val token = RefreshToken(value = stasis.test.Generators.generateString(withSize = 16))
    val json = s"""\"${token.value}\""""

    refreshTokenFormat.writes(token).toString should be(json)
    refreshTokenFormat.reads(Json.parse(json)).asOpt should be(Some(token))
  }

  they should "convert authorization codes to/from JSON" in {
    val code = Generators.generateAuthorizationCode
    val json = s"""\"${code.value}\""""

    authorizationCodeFormat.writes(code).toString should be(json)
    authorizationCodeFormat.reads(Json.parse(json)).asOpt should be(Some(code))
  }

  they should "convert seconds to/from JSON" in {
    val seconds = Seconds(42)
    val json = seconds.value.toString

    secondsFormat.writes(seconds).toString should be(json)
    secondsFormat.reads(Json.parse(json).as[JsNumber]).asOpt should be(Some(seconds))
  }

  they should "convert code challenges to/from JSON" in {
    val codeChallenge = StoredAuthorizationCode.Challenge(
      value = stasis.test.Generators.generateString(withSize = 16),
      method = Some(ChallengeMethod.S256)
    )
    val json = codeChallengeFormat.writes(codeChallenge).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("value" -> JsString(codeChallenge.value))
    parsedFields should contain("method" -> JsString("s256"))
  }

  they should "convert APIs to/from JSON" in {
    val api = Generators.generateApi
    val json = apiFormat.writes(api).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("id" -> JsString(api.id))

    apiFormat.reads(Json.parse(json).as[JsObject]).asOpt should be(Some(api))
  }

  they should "convert clients to JSON" in {
    val client = Generators.generateClient
    val json = clientWrites.writes(client).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("id" -> JsString(client.id.toString))
    parsedFields should contain("redirect_uri" -> JsString(client.redirectUri))
    parsedFields should contain("token_expiration" -> JsNumber(client.tokenExpiration.value))
    parsedFields should contain("active" -> JsBoolean(client.active))
    parsedKeys should not contain "secret"
    parsedKeys should not contain "salt"
  }

  they should "convert stored authorization codes to JSON" in {
    val code = StoredAuthorizationCode(
      code = Generators.generateAuthorizationCode,
      client = Client.generateId(),
      owner = Generators.generateResourceOwner,
      scope = Some(stasis.test.Generators.generateString(withSize = 16)),
      challenge = Some(
        StoredAuthorizationCode.Challenge(
          value = stasis.test.Generators.generateString(withSize = 16),
          method = Some(ChallengeMethod.S256)
        )
      )
    )
    val json = storedAuthorizationCodeWrites.writes(code).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("code" -> JsString(code.code.value))
    parsedFields should contain("client" -> JsString(code.client.toString))
    parsedFields should contain("scope" -> JsString(code.scope.getOrElse("invalid-scope")))
    parsedKeys should contain("owner")
    parsedKeys should contain("challenge")
  }

  they should "convert resource owners to JSON" in {
    val owner = Generators.generateResourceOwner
    val json = resourceOwnerWrites.writes(owner).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("username" -> JsString(owner.username))
    parsedFields should contain("allowed_scopes" -> JsArray(owner.allowedScopes.map(JsString)))
    parsedFields should contain("active" -> JsBoolean(owner.active))
    parsedKeys should not contain "secret"
    parsedKeys should not contain "salt"
  }

  they should "convert stored refresh tokens to JSON" in {
    val token = StoredRefreshToken(
      token = Generators.generateRefreshToken,
      client = Client.generateId(),
      owner = Generators.generateResourceOwner,
      scope = Some(stasis.test.Generators.generateString(withSize = 16)),
      expiration = Instant.now()
    )
    val json = storedRefreshTokenWrites.writes(token).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("token" -> JsString(token.token.value))
    parsedFields should contain("client" -> JsString(token.client.toString))
    parsedFields should contain("scope" -> JsString(token.scope.getOrElse("invalid-scope")))
    parsedKeys should contain("owner")
    parsedKeys should contain("expiration")
  }
}
