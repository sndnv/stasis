package stasis.core.security.oauth

import stasis.core.security.oauth.OAuthClient.AccessTokenResponse

import scala.concurrent.Future

trait OAuthClient {
  def token(scope: Option[String], parameters: OAuthClient.GrantParameters): Future[AccessTokenResponse]
}

object OAuthClient {
  import play.api.libs.json.{Format, Json}

  object GrantType {
    final val ClientCredentials: String = "client_credentials"
    final val ResourceOwnerPasswordCredentials: String = "password"
    final val RefreshToken: String = "refresh_token"
  }

  sealed trait GrantParameters
  object GrantParameters {
    final case class ClientCredentials() extends GrantParameters
    final case class ResourceOwnerPasswordCredentials(username: String, password: String) extends GrantParameters
    final case class RefreshToken(refreshToken: String) extends GrantParameters
  }

  implicit val accessTokenResponseFormat: Format[AccessTokenResponse] = Json.format[AccessTokenResponse]

  final case class AccessTokenResponse(
    access_token: String,
    refresh_token: Option[String],
    expires_in: Long,
    scope: Option[String]
  )
}
