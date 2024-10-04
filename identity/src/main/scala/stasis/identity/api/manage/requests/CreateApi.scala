package stasis.identity.api.manage.requests

import stasis.identity.model.apis.Api

final case class CreateApi(id: Api.Id) {
  require(id.nonEmpty, "id must not be empty")

  def toApi: Api =
    Api.create(id = id)
}
