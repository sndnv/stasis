package stasis.identity.api.manage.requests

import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.realms.Realm
import stasis.identity.model.secrets.Secret

final case class CreateOwner(
  username: ResourceOwner.Id,
  rawPassword: String,
  allowedScopes: Seq[String]
) {
  require(username.nonEmpty, "username must not be empty")
  require(allowedScopes.nonEmpty, "allowed scopes must not be empty")
  require(rawPassword.nonEmpty, "password must not be empty")

  def toResourceOwner(realm: Realm.Id)(implicit config: Secret.ResourceOwnerConfig): ResourceOwner = {
    val salt = Secret.generateSalt()

    ResourceOwner(
      username = username,
      password = Secret.derive(rawSecret = rawPassword, salt = salt),
      salt = salt,
      realm = realm,
      allowedScopes = allowedScopes,
      active = true
    )
  }
}
