package stasis.identity.api

import java.time.Instant

import play.api.libs.json._

import stasis.identity.api.Formats._
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.Generators
import stasis.identity.model.GrantType
import stasis.identity.model.ResponseType
import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.errors.TokenError
import stasis.identity.model.tokens.AccessToken
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.model.tokens.TokenType
import io.github.sndnv.layers
import io.github.sndnv.layers.testing.UnitSpec

class FormatsSpec extends UnitSpec {
  "Formats" should "convert authorization errors to JSON" in withRetry {
    val error = AuthorizationError.InvalidScope(withState = layers.testing.Generators.generateString(withSize = 16))
    val json = authorizationErrorWrites.writes(error).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("error" -> Json.toJson(error.error))
    parsedFields should contain("error_description" -> Json.toJson(error.error_description))
    parsedFields should contain("state" -> Json.toJson(error.state))
  }

  they should "convert token errors to JSON" in withRetry {
    val error = TokenError.InvalidScope
    val json = tokenErrorWrites.writes(error).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("error" -> Json.toJson(error.error))
    parsedFields should contain("error_description" -> Json.toJson(error.error_description))
  }

  they should "convert token types to/from JSON" in withRetry {
    val json = "\"bearer\""
    tokenTypeFormat.writes(TokenType.Bearer).toString should be(json)
    tokenTypeFormat.reads(Json.parse(json)).asOpt should be(Some(TokenType.Bearer))
  }

  they should "convert grant types to/from JSON" in withRetry {
    val grants = Map(
      GrantType.AuthorizationCode -> "\"authorization_code\"",
      GrantType.ClientCredentials -> "\"client_credentials\"",
      GrantType.Implicit -> "\"implicit\"",
      GrantType.RefreshToken -> "\"refresh_token\"",
      GrantType.Password -> "\"password\""
    )

    grants.foreach { case (grant, json) =>
      grantTypeFormat.writes(grant).toString should be(json)
      grantTypeFormat.reads(Json.parse(json)).asOpt should be(Some(grant))
    }

    succeed
  }

  they should "convert challenge methods t/from JSON" in withRetry {
    val challenges = Map(
      ChallengeMethod.Plain -> "\"plain\"",
      ChallengeMethod.S256 -> "\"s256\""
    )

    challenges.foreach { case (challenge, json) =>
      challengeMethodFormat.writes(challenge).toString should be(json)
      challengeMethodFormat.reads(Json.parse(json)).asOpt should be(Some(challenge))
    }

    succeed
  }

  they should "convert response types to/from JSON" in withRetry {
    val responses = Map(
      ResponseType.Code -> "\"code\"",
      ResponseType.Token -> "\"token\""
    )

    responses.foreach { case (response, json) =>
      responseTypeFormat.writes(response).toString should be(json)
      responseTypeFormat.reads(Json.parse(json)).asOpt should be(Some(response))
    }

    succeed
  }

  they should "convert access tokens to/from JSON" in withRetry {
    val token = AccessToken(value = layers.testing.Generators.generateString(withSize = 16))
    val json = s"""\"${token.value}\""""

    accessTokenFormat.writes(token).toString should be(json)
    accessTokenFormat.reads(Json.parse(json)).asOpt should be(Some(token))
  }

  they should "convert refresh tokens to/from JSON" in withRetry {
    val token = RefreshToken(value = layers.testing.Generators.generateString(withSize = 16))
    val json = s"""\"${token.value}\""""

    refreshTokenFormat.writes(token).toString should be(json)
    refreshTokenFormat.reads(Json.parse(json)).asOpt should be(Some(token))
  }

  they should "convert authorization codes to/from JSON" in withRetry {
    val code = Generators.generateAuthorizationCode
    val json = s"""\"${code.value}\""""

    authorizationCodeFormat.writes(code).toString should be(json)
    authorizationCodeFormat.reads(Json.parse(json)).asOpt should be(Some(code))
  }

  they should "convert seconds to/from JSON" in withRetry {
    val seconds = Seconds(42)
    val json = seconds.value.toString

    secondsFormat.writes(seconds).toString should be(json)
    secondsFormat.reads(Json.parse(json).as[JsNumber]).asOpt should be(Some(seconds))
  }

  they should "convert code challenges to/from JSON" in withRetry {
    val codeChallenge = StoredAuthorizationCode.Challenge(
      value = layers.testing.Generators.generateString(withSize = 16),
      method = Some(ChallengeMethod.S256)
    )
    val json = codeChallengeFormat.writes(codeChallenge).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("value" -> Json.toJson(codeChallenge.value))
    parsedFields should contain("method" -> Json.toJson("s256"))
  }

  they should "convert APIs to/from JSON" in withRetry {
    val api = Generators.generateApi
    val json = apiFormat.writes(api).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("id" -> Json.toJson(api.id))
    parsedFields should contain("created" -> Json.toJson(api.created))
    parsedFields should contain("updated" -> Json.toJson(api.updated))

    apiFormat.reads(Json.parse(json).as[JsObject]).asOpt should be(Some(api))
  }

  they should "convert clients to JSON" in withRetry {
    val client = Generators.generateClient
    val json = clientWrites.writes(client).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("id" -> Json.toJson(client.id.toString))
    parsedFields should contain("redirect_uri" -> Json.toJson(client.redirectUri))
    parsedFields should contain("token_expiration" -> Json.toJson(client.tokenExpiration.value))
    parsedFields should contain("active" -> Json.toJson(client.active))
    parsedFields should contain("created" -> Json.toJson(client.created))
    parsedFields should contain("updated" -> Json.toJson(client.updated))
    parsedKeys should not contain "secret"
    parsedKeys should not contain "salt"
  }

  they should "convert stored authorization codes to JSON" in withRetry {
    val code = StoredAuthorizationCode(
      code = Generators.generateAuthorizationCode,
      client = Client.generateId(),
      owner = Generators.generateResourceOwner,
      scope = Some(layers.testing.Generators.generateString(withSize = 16)),
      challenge = Some(
        StoredAuthorizationCode.Challenge(
          value = layers.testing.Generators.generateString(withSize = 16),
          method = Some(ChallengeMethod.S256)
        )
      ),
      created = Instant.now()
    )
    val json = storedAuthorizationCodeWrites.writes(code).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("code" -> Json.toJson(code.code.value))
    parsedFields should contain("client" -> Json.toJson(code.client.toString))
    parsedFields should contain("scope" -> Json.toJson(code.scope.getOrElse("invalid-scope")))
    parsedFields should contain("created" -> Json.toJson(code.created))
    parsedKeys should contain("owner")
    parsedKeys should contain("challenge")
  }

  they should "convert resource owners to JSON" in withRetry {
    val owner = Generators.generateResourceOwner
    val json = resourceOwnerWrites.writes(owner).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("username" -> Json.toJson(owner.username))
    parsedFields should contain("allowed_scopes" -> Json.toJson(owner.allowedScopes.map(Json.toJson(_: String))))
    parsedFields should contain("active" -> Json.toJson(owner.active))
    parsedFields should contain("created" -> Json.toJson(owner.created))
    parsedFields should contain("updated" -> Json.toJson(owner.updated))
    parsedKeys should not contain "secret"
    parsedKeys should not contain "salt"
  }

  they should "convert stored refresh tokens to JSON" in withRetry {
    val token = StoredRefreshToken(
      token = Generators.generateRefreshToken,
      client = Client.generateId(),
      owner = Generators.generateResourceOwner.username,
      scope = Some(layers.testing.Generators.generateString(withSize = 16)),
      expiration = Instant.now(),
      created = Instant.now()
    )
    val json = storedRefreshTokenWrites.writes(token).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("token" -> Json.toJson(token.token.value))
    parsedFields should contain("client" -> Json.toJson(token.client.toString))
    parsedFields should contain("scope" -> Json.toJson(token.scope.getOrElse("invalid-scope")))
    parsedFields should contain("created" -> Json.toJson(token.created))
    parsedKeys should contain("owner")
    parsedKeys should contain("expiration")
  }
}
