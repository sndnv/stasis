package stasis.identity.model.apis

final case class Api(
  id: Api.Id
) {
  require(
    id.matches("^[a-zA-Z0-9\\-_]+$"),
    "API identifier must be non-empty and contain only alphanumeric characters, '-' and '_'"
  )
}

object Api {
  type Id = String

  final val ManageIdentity: Id = "manage-identity"
}
