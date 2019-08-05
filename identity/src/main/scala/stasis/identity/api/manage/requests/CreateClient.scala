package stasis.identity.api.manage.requests

import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret

final case class CreateClient(
  allowedScopes: Seq[String],
  redirectUri: String,
  tokenExpiration: Seconds,
  rawSecret: String
) {
  require(allowedScopes.nonEmpty, "allowed scopes must not be empty")
  require(redirectUri.nonEmpty, "redirect URI must not be empty")
  require(tokenExpiration.value > 0, "token expiration must be a positive number")
  require(rawSecret.nonEmpty, "secret must not be empty")

  def toClient(implicit config: Secret.ClientConfig): Client = {
    val salt = Secret.generateSalt()

    Client(
      id = Client.generateId(),
      allowedScopes = allowedScopes,
      redirectUri = redirectUri,
      tokenExpiration = tokenExpiration,
      secret = Secret.derive(rawSecret = rawSecret, salt = salt),
      salt = salt,
      active = true
    )
  }
}
