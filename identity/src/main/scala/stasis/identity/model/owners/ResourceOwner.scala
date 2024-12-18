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
) {
  require(!username.isBlank, "Resource owner username cannot be blank")
  require(password.value.nonEmpty, "Resource owner password cannot be empty")
  require(!salt.isBlank, "Resource owner salt cannot be blank")
  require(subject.forall(!_.isBlank), "Resource owner subject cannot be blank")
}

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
