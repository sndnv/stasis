package stasis.identity.model.owners

import java.time.Instant

import stasis.identity.model.secrets.Secret

final case class ResourceOwner(
  username: ResourceOwner.Id,
  password: Secret,
  salt: String,
  allowedScopes: Seq[String],
  active: Boolean,
  subject: Option[String],
  created: Instant,
  updated: Instant
)

object ResourceOwner {
  def create(
    username: ResourceOwner.Id,
    password: Secret,
    salt: String,
    allowedScopes: Seq[String],
    active: Boolean,
    subject: Option[String]
  ): ResourceOwner = {
    val now = Instant.now()
    ResourceOwner(
      username = username,
      password = password,
      salt = salt,
      allowedScopes = allowedScopes,
      active = active,
      subject = subject,
      created = now,
      updated = now
    )
  }

  type Id = String
}
