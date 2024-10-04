package stasis.identity.api.manage.requests

import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret

final case class CreateOwner(
  username: ResourceOwner.Id,
  rawPassword: String,
  allowedScopes: Seq[String],
  subject: Option[String]
) {
  require(username.nonEmpty, "username must not be empty")
  require(rawPassword.nonEmpty, "password must not be empty")
  require(subject.forall(_.nonEmpty), "subject must not be empty")

  def toResourceOwner(implicit config: Secret.ResourceOwnerConfig): ResourceOwner = {
    val salt = Secret.generateSalt()

    ResourceOwner.create(
      username = username,
      password = Secret.derive(rawSecret = rawPassword, salt = salt),
      salt = salt,
      allowedScopes = allowedScopes,
      active = true,
      subject = subject
    )
  }
}
