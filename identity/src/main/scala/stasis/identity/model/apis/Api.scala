package stasis.identity.model.apis

import stasis.identity.model.realms.Realm

final case class Api(
  id: Api.Id,
  realm: Realm.Id
)

object Api {
  type Id = String
}
