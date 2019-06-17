package stasis.test.specs.unit.identity.api

import play.api.libs.json._
import stasis.identity.api.Formats._
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.errors.{AuthorizationError, TokenError}
import stasis.identity.model.tokens.{AccessToken, RefreshToken, StoredRefreshToken, TokenType}
import stasis.identity.model.{GrantType, ResponseType, Seconds}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.identity.model.Generators

class FormatsSpec extends UnitSpec {
  "Formats" should "convert authorization errors to JSON" in {
    val error = AuthorizationError.InvalidScope(withState = Generators.generateString(withSize = 16))
    val json = authorizationErrorFormat.writes(error).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("error" -> JsString(error.error))
    parsedFields should contain("error_description" -> JsString(error.error_description))
    parsedFields should contain("state" -> JsString(error.state))
  }

  they should "convert token errors to JSON" in {
    val error = TokenError.InvalidScope
    val json = tokenErrorFormat.writes(error).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("error" -> JsString(error.error))
    parsedFields should contain("error_description" -> JsString(error.error_description))
  }

  they should "convert token types to/from JSON" in {
    val json = "\"bearer\""
    tokenTypeFormat.writes(TokenType.Bearer).toString should be(json)
    tokenTypeFormat.reads(Json.parse(json).as[JsString]).asOpt should be(Some(TokenType.Bearer))
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
        grantTypeFormat.reads(Json.parse(json).as[JsString]).asOpt should be(Some(grant))
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
        responseTypeFormat.reads(Json.parse(json).as[JsString]).asOpt should be(Some(response))
    }
  }

  they should "convert access tokens to/from JSON" in {
    val token = AccessToken(value = Generators.generateString(withSize = 16))
    val json = s"""\"${token.value}\""""

    accessTokenFormat.writes(token).toString should be(json)
    accessTokenFormat.reads(Json.parse(json).as[JsString]).asOpt should be(Some(token))
  }

  they should "convert refresh tokens to/from JSON" in {
    val token = RefreshToken(value = Generators.generateString(withSize = 16))
    val json = s"""\"${token.value}\""""

    refreshTokenFormat.writes(token).toString should be(json)
    refreshTokenFormat.reads(Json.parse(json).as[JsString]).asOpt should be(Some(token))
  }

  they should "convert authorization codes to/from JSON" in {
    val code = Generators.generateAuthorizationCode
    val json = s"""\"${code.value}\""""

    authorizationCodeFormat.writes(code).toString should be(json)
    authorizationCodeFormat.reads(Json.parse(json).as[JsString]).asOpt should be(Some(code))
  }

  they should "convert seconds to/from JSON" in {
    val seconds = Seconds(42)
    val json = seconds.value.toString

    secondsFormat.writes(seconds).toString should be(json)
    secondsFormat.reads(Json.parse(json).as[JsNumber]).asOpt should be(Some(seconds))
  }

  they should "convert APIs to/from JSON" in {
    val api = Generators.generateApi
    val json = apiFormat.writes(api).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("id" -> JsString(api.id))
    parsedFields should contain("realm" -> JsString(api.realm))

    apiFormat.reads(Json.parse(json).as[JsObject]).asOpt should be(Some(api))
  }

  they should "convert clients to JSON" in {
    val client = Generators.generateClient
    val json = clientFormat.writes(client).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("id" -> JsString(client.id.toString))
    parsedFields should contain("realm" -> JsString(client.realm))
    parsedFields should contain("allowedScopes" -> JsArray(client.allowedScopes.map(JsString)))
    parsedFields should contain("redirectUri" -> JsString(client.redirectUri))
    parsedFields should contain("tokenExpiration" -> JsNumber(client.tokenExpiration.value))
    parsedFields should contain("active" -> JsBoolean(client.active))
    parsedKeys should not contain "secret"
    parsedKeys should not contain "salt"
  }

  they should "convert stored authorization codes to JSON" in {
    val code = StoredAuthorizationCode(
      code = Generators.generateAuthorizationCode,
      owner = Generators.generateResourceOwner,
      scope = Some(Generators.generateString(withSize = 16))
    )
    val json = storedAuthorizationCodeFormat.writes(code).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("code" -> JsString(code.code.value))
    parsedFields should contain("scope" -> JsString(code.scope.getOrElse("invalid-scope")))
    parsedKeys should contain("owner")
  }

  they should "convert resource owners to JSON" in {
    val owner = Generators.generateResourceOwner
    val json = resourceOwnerFormat.writes(owner).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("username" -> JsString(owner.username))
    parsedFields should contain("realm" -> JsString(owner.realm))
    parsedFields should contain("allowedScopes" -> JsArray(owner.allowedScopes.map(JsString)))
    parsedFields should contain("active" -> JsBoolean(owner.active))
    parsedKeys should not contain "secret"
    parsedKeys should not contain "salt"
  }

  they should "convert realms to/from JSON" in {
    val realm = Generators.generateRealm
    val json = realmFormat.writes(realm).toString
    val parsedFields = Json.parse(json).as[JsObject].fields

    parsedFields should contain("id" -> JsString(realm.id))
    parsedFields should contain("refreshTokensAllowed" -> JsBoolean(realm.refreshTokensAllowed))

    realmFormat.reads(Json.parse(json).as[JsObject]).asOpt should be(Some(realm))
  }

  they should "convert stored refresh tokens to JSON" in {
    val token = StoredRefreshToken(
      token = Generators.generateRefreshToken,
      owner = Generators.generateResourceOwner,
      scope = Some(Generators.generateString(withSize = 16))
    )
    val json = storedRefreshTokenFormat.writes(token).toString
    val parsedFields = Json.parse(json).as[JsObject].fields
    val parsedKeys = parsedFields.map(_._1)

    parsedFields should contain("token" -> JsString(token.token.value))
    parsedFields should contain("scope" -> JsString(token.scope.getOrElse("invalid-scope")))
    parsedKeys should contain("owner")
  }
}
