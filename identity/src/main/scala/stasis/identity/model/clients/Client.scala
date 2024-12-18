package stasis.identity.model.clients

import java.time.Instant

import stasis.identity.model.Seconds
import stasis.identity.model.secrets.Secret

final case class Client(
  id: Client.Id,
  redirectUri: String,
  tokenExpiration: Seconds,
  secret: Secret,
  salt: String,
  active: Boolean,
  subject: Option[String],
  created: Instant,
  updated: Instant
) {
  require(!redirectUri.isBlank, "Client redirect URI cannot be blank")
  require(tokenExpiration.value > 0, "Client token expiration cannot be negative or 0")
  require(secret.value.nonEmpty, "Client secret cannot be empty")
  require(!salt.isBlank, "Client salt cannot be blank")
  require(subject.forall(!_.isBlank), "Client subject cannot be blank")
}

object Client {
  def create(
    id: Client.Id,
    redirectUri: String,
    tokenExpiration: Seconds,
    secret: Secret,
    salt: String,
    active: Boolean,
    subject: Option[String]
  ): Client = {
    val now = Instant.now()

    Client(
      id = id,
      redirectUri = redirectUri,
      tokenExpiration = tokenExpiration,
      secret = secret,
      salt = salt,
      active = active,
      subject = subject,
      created = now,
      updated = now
    )
  }

  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
