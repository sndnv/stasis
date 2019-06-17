package stasis.identity.api.manage.requests

import stasis.identity.model.secrets.Secret

final case class UpdateClientCredentials(
  rawSecret: String
) {
  require(rawSecret.nonEmpty, "secret must not be empty")

  def toSecret()(implicit config: Secret.ClientConfig): (Secret, String) = {
    val salt = Secret.generateSalt()
    val secret = Secret.derive(rawSecret = rawSecret, salt = salt)

    (secret, salt)
  }
}
