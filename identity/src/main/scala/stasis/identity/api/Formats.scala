package stasis.identity.api

import akka.http.scaladsl.unmarshalling.Unmarshaller
import play.api.libs.json._
import stasis.identity.api.manage.requests._
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.{AuthorizationCode, StoredAuthorizationCode}
import stasis.identity.model.errors.{AuthorizationError, TokenError}
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.{AccessToken, RefreshToken, StoredRefreshToken, TokenType}
import stasis.identity.model.{ChallengeMethod, GrantType, ResponseType, Seconds}

object Formats {
  implicit val authorizationErrorWrites: Writes[AuthorizationError] = Writes[AuthorizationError] { error =>
    Json.obj(
      "error" -> JsString(error.error),
      "error_description" -> JsString(error.error_description),
      "state" -> JsString(error.state)
    )
  }

  implicit val tokenErrorWrites: Writes[TokenError] = Writes[TokenError] { error =>
    Json.obj(
      "error" -> JsString(error.error),
      "error_description" -> JsString(error.error_description)
    )
  }

  implicit val tokenTypeFormat: Format[TokenType] = Format[TokenType](
    fjs = Reads[TokenType](_.validate[String].map { case "bearer" => TokenType.Bearer }),
    tjs = Writes[TokenType](tokenType => JsString(tokenType.toString.toLowerCase))
  )

  implicit val grantTypeFormat: Format[GrantType] = Format[GrantType](
    fjs = Reads[GrantType](_.validate[String].map(convertStringToGrantType)),
    tjs = Writes[GrantType](grantType => JsString(convertGrantTypeToString(grantType)))
  )

  implicit val challengeMethodFormat: Format[ChallengeMethod] = Format[ChallengeMethod](
    fjs = Reads[ChallengeMethod](_.validate[String].map(convertStringToChallengeMethod)),
    tjs = Writes[ChallengeMethod](challengeMethod => JsString(convertChallengeMethodToString(challengeMethod)))
  )

  implicit val responseTypeFormat: Format[ResponseType] = Format[ResponseType](
    fjs = Reads[ResponseType](_.validate[String].map(convertStringToResponseType)),
    tjs = Writes[ResponseType](responseType => JsString(convertResponseTypeToString(responseType)))
  )

  implicit val accessTokenFormat: Format[AccessToken] =
    Format(
      fjs = Reads[AccessToken](_.validate[String].map(AccessToken)),
      tjs = Writes[AccessToken](token => JsString(token.value))
    )

  implicit val refreshTokenFormat: Format[RefreshToken] =
    Format(
      fjs = Reads[RefreshToken](_.validate[String].map(RefreshToken)),
      tjs = Writes[RefreshToken](token => JsString(token.value))
    )

  implicit val authorizationCodeFormat: Format[AuthorizationCode] =
    Format(
      fjs = Reads[AuthorizationCode](_.validate[String].map(AuthorizationCode)),
      tjs = Writes[AuthorizationCode](code => JsString(code.value))
    )

  implicit val secondsFormat: Format[Seconds] =
    Format(
      fjs = Reads[Seconds](_.validate[Long].map(Seconds.apply)),
      tjs = Writes[Seconds](seconds => JsNumber(seconds.value))
    )

  implicit val codeChallengeFormat: Format[StoredAuthorizationCode.Challenge] =
    Json.format[StoredAuthorizationCode.Challenge]

  implicit val apiFormat: Format[Api] = Json.format[Api]

  implicit val clientWrites: Writes[Client] =
    Writes[Client](
      client =>
        Json.obj(
          "id" -> Json.toJson(client.id),
          "allowedScopes" -> Json.toJson(client.allowedScopes),
          "redirectUri" -> Json.toJson(client.redirectUri),
          "tokenExpiration" -> Json.toJson(client.tokenExpiration),
          "active" -> Json.toJson(client.active)
      )
    )

  implicit val storedAuthorizationCodeWrites: Writes[StoredAuthorizationCode] =
    Writes[StoredAuthorizationCode](
      code =>
        Json.obj(
          "code" -> Json.toJson(code.code),
          "client" -> Json.toJson(code.client),
          "owner" -> Json.toJson(code.owner.username),
          "scope" -> Json.toJson(code.scope),
          "challenge" -> Json.toJson(code.challenge)
      )
    )

  implicit val resourceOwnerWrites: Writes[ResourceOwner] =
    Writes[ResourceOwner](
      owner =>
        Json.obj(
          "username" -> Json.toJson(owner.username),
          "allowedScopes" -> Json.toJson(owner.allowedScopes),
          "active" -> Json.toJson(owner.active)
      )
    )

  implicit val storedRefreshTokenWrites: Writes[StoredRefreshToken] =
    Writes[StoredRefreshToken](
      token =>
        Json.obj(
          "token" -> Json.toJson(token.token),
          "client" -> Json.toJson(token.client),
          "owner" -> Json.toJson(token.owner.username),
          "scope" -> Json.toJson(token.scope),
          "expiration" -> Json.toJson(token.expiration)
      )
    )

  implicit val crateApiFormat: Format[CreateApi] = Json.format[CreateApi]
  implicit val createClientFormat: Format[CreateClient] = Json.format[CreateClient]
  implicit val updateClientFormat: Format[UpdateClient] = Json.format[UpdateClient]
  implicit val updateClientCredentialsFormat: Format[UpdateClientCredentials] = Json.format[UpdateClientCredentials]
  implicit val createdClientFormat: Format[CreatedClient] = Json.format[CreatedClient]
  implicit val createOwnerFormat: Format[CreateOwner] = Json.format[CreateOwner]
  implicit val updateOwnerFormat: Format[UpdateOwner] = Json.format[UpdateOwner]
  implicit val updateOwnerCredentialsFormat: Format[UpdateOwnerCredentials] = Json.format[UpdateOwnerCredentials]

  implicit val stringToChallengeMethod: Unmarshaller[String, ChallengeMethod] =
    Unmarshaller.strict { convertStringToChallengeMethod }

  implicit val stringToResponseType: Unmarshaller[String, ResponseType] =
    Unmarshaller.strict { convertStringToResponseType }

  implicit val stringToGrantType: Unmarshaller[String, GrantType] =
    Unmarshaller.strict { convertStringToGrantType }

  implicit val stringToAuthorizationCode: Unmarshaller[String, AuthorizationCode] =
    Unmarshaller.strict(AuthorizationCode)

  implicit val stringToUuid: Unmarshaller[String, java.util.UUID] =
    Unmarshaller.strict(java.util.UUID.fromString)

  implicit val stringToRefreshToken: Unmarshaller[String, RefreshToken] =
    Unmarshaller.strict(RefreshToken)

  private def convertStringToChallengeMethod(string: String): ChallengeMethod = string.toLowerCase match {
    case "plain" => ChallengeMethod.Plain
    case "s256"  => ChallengeMethod.S256
  }

  private def convertChallengeMethodToString(challengeMethod: ChallengeMethod): String =
    challengeMethod.toString.toLowerCase

  private def convertStringToResponseType(string: String): ResponseType = string match {
    case "code"  => ResponseType.Code
    case "token" => ResponseType.Token
  }

  private def convertResponseTypeToString(responseType: ResponseType): String =
    responseType.toString.toLowerCase

  private def convertStringToGrantType(string: String): GrantType = string match {
    case "authorization_code" => GrantType.AuthorizationCode
    case "client_credentials" => GrantType.ClientCredentials
    case "implicit"           => GrantType.Implicit
    case "refresh_token"      => GrantType.RefreshToken
    case "password"           => GrantType.Password
  }

  private def convertGrantTypeToString(grantType: GrantType): String = grantType match {
    case GrantType.AuthorizationCode => "authorization_code"
    case GrantType.ClientCredentials => "client_credentials"
    case GrantType.Implicit          => "implicit"
    case GrantType.RefreshToken      => "refresh_token"
    case GrantType.Password          => "password"
  }
}
