package stasis.identity.api.manage.requests

import stasis.identity.model.secrets.Secret

final case class UpdateOwnerCredentials(
  rawPassword: String
) {
  require(rawPassword.nonEmpty, "password must not be empty")

  def toSecret()(implicit config: Secret.ResourceOwnerConfig): (Secret, String) = {
    val salt = Secret.generateSalt()
    val secret = Secret.derive(rawSecret = rawPassword, salt = salt)

    (secret, salt)
  }
}
