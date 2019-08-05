package stasis.identity.model.owners

import stasis.identity.model.secrets.Secret

final case class ResourceOwner(
  username: ResourceOwner.Id,
  password: Secret,
  salt: String,
  allowedScopes: Seq[String],
  active: Boolean
)

object ResourceOwner {
  type Id = String
}
