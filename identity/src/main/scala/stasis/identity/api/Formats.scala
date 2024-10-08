package stasis.identity.api

import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import play.api.libs.json._

import stasis.identity.api.manage.requests._
import stasis.identity.api.manage.responses.CreatedClient
import stasis.identity.model.ChallengeMethod
import stasis.identity.model.GrantType
import stasis.identity.model.ResponseType
import stasis.identity.model.Seconds
import stasis.identity.model.apis.Api
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.model.errors.AuthorizationError
import stasis.identity.model.errors.TokenError
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.AccessToken
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.model.tokens.TokenType

object Formats {
  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val authorizationErrorWrites: Writes[AuthorizationError] = Writes[AuthorizationError] { error =>
    Json.obj(
      "error" -> Json.toJson(error.error),
      "error_description" -> Json.toJson(error.error_description),
      "state" -> Json.toJson(error.state)
    )
  }

  implicit val tokenErrorWrites: Writes[TokenError] = Writes[TokenError] { error =>
    Json.obj(
      "error" -> Json.toJson(error.error),
      "error_description" -> Json.toJson(error.error_description)
    )
  }

  implicit val tokenTypeFormat: Format[TokenType] = Format[TokenType](
    fjs = Reads[TokenType](_.validate[String].map { case "bearer" => TokenType.Bearer }),
    tjs = Writes[TokenType](tokenType => Json.toJson(tokenType.toString.toLowerCase))
  )

  implicit val grantTypeFormat: Format[GrantType] = Format[GrantType](
    fjs = Reads[GrantType](_.validate[String].map(convertStringToGrantType)),
    tjs = Writes[GrantType](grantType => Json.toJson(convertGrantTypeToString(grantType)))
  )

  implicit val challengeMethodFormat: Format[ChallengeMethod] = Format[ChallengeMethod](
    fjs = Reads[ChallengeMethod](_.validate[String].map(convertStringToChallengeMethod)),
    tjs = Writes[ChallengeMethod](challengeMethod => Json.toJson(convertChallengeMethodToString(challengeMethod)))
  )

  implicit val responseTypeFormat: Format[ResponseType] = Format[ResponseType](
    fjs = Reads[ResponseType](_.validate[String].map(convertStringToResponseType)),
    tjs = Writes[ResponseType](responseType => Json.toJson(convertResponseTypeToString(responseType)))
  )

  implicit val accessTokenFormat: Format[AccessToken] =
    Format(
      fjs = Reads[AccessToken](_.validate[String].map(AccessToken)),
      tjs = Writes[AccessToken](token => Json.toJson(token.value))
    )

  implicit val refreshTokenFormat: Format[RefreshToken] =
    Format(
      fjs = Reads[RefreshToken](_.validate[String].map(RefreshToken)),
      tjs = Writes[RefreshToken](token => Json.toJson(token.value))
    )

  implicit val authorizationCodeFormat: Format[AuthorizationCode] =
    Format(
      fjs = Reads[AuthorizationCode](_.validate[String].map(AuthorizationCode)),
      tjs = Writes[AuthorizationCode](code => Json.toJson(code.value))
    )

  implicit val secondsFormat: Format[Seconds] =
    Format(
      fjs = Reads[Seconds](_.validate[Long].map(Seconds.apply)),
      tjs = Writes[Seconds](seconds => Json.toJson(seconds.value))
    )

  implicit val codeChallengeFormat: Format[StoredAuthorizationCode.Challenge] =
    Json.format[StoredAuthorizationCode.Challenge]

  implicit val apiFormat: Format[Api] = Json.format[Api]

  implicit val clientWrites: Writes[Client] =
    Writes[Client](client =>
      Json.obj(
        "id" -> Json.toJson(client.id),
        "redirect_uri" -> Json.toJson(client.redirectUri),
        "token_expiration" -> Json.toJson(client.tokenExpiration),
        "active" -> Json.toJson(client.active),
        "subject" -> Json.toJson(client.subject),
        "created" -> Json.toJson(client.created),
        "updated" -> Json.toJson(client.updated)
      )
    )

  implicit val storedAuthorizationCodeWrites: Writes[StoredAuthorizationCode] =
    Writes[StoredAuthorizationCode](code =>
      Json.obj(
        "code" -> Json.toJson(code.code),
        "client" -> Json.toJson(code.client),
        "owner" -> Json.toJson(code.owner.username),
        "scope" -> Json.toJson(code.scope),
        "challenge" -> Json.toJson(code.challenge),
        "created" -> Json.toJson(code.created)
      )
    )

  implicit val resourceOwnerWrites: Writes[ResourceOwner] =
    Writes[ResourceOwner](owner =>
      Json.obj(
        "username" -> Json.toJson(owner.username),
        "allowed_scopes" -> Json.toJson(owner.allowedScopes),
        "active" -> Json.toJson(owner.active),
        "subject" -> Json.toJson(owner.subject),
        "created" -> Json.toJson(owner.created),
        "updated" -> Json.toJson(owner.updated)
      )
    )

  implicit val storedRefreshTokenWrites: Writes[StoredRefreshToken] =
    Writes[StoredRefreshToken](token =>
      Json.obj(
        "token" -> Json.toJson(token.token),
        "client" -> Json.toJson(token.client),
        "owner" -> Json.toJson(token.owner),
        "scope" -> Json.toJson(token.scope),
        "expiration" -> Json.toJson(token.expiration),
        "created" -> Json.toJson(token.created)
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

  private def convertStringToChallengeMethod(string: String): ChallengeMethod =
    string.toLowerCase match {
      case "plain" => ChallengeMethod.Plain
      case "s256"  => ChallengeMethod.S256
    }

  private def convertChallengeMethodToString(challengeMethod: ChallengeMethod): String =
    challengeMethod.toString.toLowerCase

  private def convertStringToResponseType(string: String): ResponseType =
    string match {
      case "code"  => ResponseType.Code
      case "token" => ResponseType.Token
    }

  private def convertResponseTypeToString(responseType: ResponseType): String =
    responseType.toString.toLowerCase

  private def convertStringToGrantType(string: String): GrantType =
    string match {
      case "authorization_code" => GrantType.AuthorizationCode
      case "client_credentials" => GrantType.ClientCredentials
      case "implicit"           => GrantType.Implicit
      case "refresh_token"      => GrantType.RefreshToken
      case "password"           => GrantType.Password
    }

  private def convertGrantTypeToString(grantType: GrantType): String =
    grantType match {
      case GrantType.AuthorizationCode => "authorization_code"
      case GrantType.ClientCredentials => "client_credentials"
      case GrantType.Implicit          => "implicit"
      case GrantType.RefreshToken      => "refresh_token"
      case GrantType.Password          => "password"
    }
}
