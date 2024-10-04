package stasis.layers.security.oauth

import scala.concurrent.Future

import OAuthClient.AccessTokenResponse

trait OAuthClient {
  def tokenEndpoint: String
  def token(scope: Option[String], parameters: OAuthClient.GrantParameters): Future[AccessTokenResponse]
}

object OAuthClient {
  import play.api.libs.json.Format
  import play.api.libs.json.Json

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
