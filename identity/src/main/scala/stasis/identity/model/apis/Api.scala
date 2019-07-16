package stasis.identity.model.apis

import stasis.identity.model.realms.Realm

final case class Api(
  id: Api.Id,
  realm: Realm.Id
) {
  require(
    id.matches("^[a-zA-Z0-9\\-_]+$"),
    "API identifier must be non-empty and contain only alphanumeric characters, '-' and '_'"
  )
}

object Api {
  type Id = String

  final val ManageMaster: Id = "manage-master"
}
