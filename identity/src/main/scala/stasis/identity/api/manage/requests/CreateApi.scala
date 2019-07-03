package stasis.identity.api.manage.requests

import stasis.identity.model.apis.Api
import stasis.identity.model.realms.Realm

final case class CreateApi(id: Api.Id) {
  require(id.nonEmpty, "id must not be empty")

  def toApi(realm: Realm.Id): Api =
    Api(id = id, realm = realm)
}
