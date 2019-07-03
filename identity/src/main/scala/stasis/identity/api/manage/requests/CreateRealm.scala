package stasis.identity.api.manage.requests

import stasis.identity.model.realms.Realm

final case class CreateRealm(
  id: Realm.Id,
  refreshTokensAllowed: Boolean
) {
  require(id.nonEmpty, "identifier must be empty")

  def toRealm: Realm = Realm(id = id, refreshTokensAllowed = refreshTokensAllowed)
}
