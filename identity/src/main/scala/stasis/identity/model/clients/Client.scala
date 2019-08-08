package stasis.identity.model.clients

import stasis.identity.model.Seconds
import stasis.identity.model.secrets.Secret

final case class Client(
  id: Client.Id,
  redirectUri: String,
  tokenExpiration: Seconds,
  secret: Secret,
  salt: String,
  active: Boolean
)

object Client {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
