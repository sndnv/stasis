package stasis.identity.model.owners

import stasis.identity.model.realms.Realm
import stasis.identity.model.secrets.Secret

final case class ResourceOwner(
  username: ResourceOwner.Id,
  password: Secret,
  salt: String,
  realm: Realm.Id,
  allowedScopes: Seq[String],
  active: Boolean
)

object ResourceOwner {
  type Id = String
}
