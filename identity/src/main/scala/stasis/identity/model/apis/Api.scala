package stasis.identity.model.apis

import java.time.Instant

final case class Api(
  id: Api.Id,
  created: Instant,
  updated: Instant
) {
  require(
    id.matches("^[a-zA-Z0-9\\-_]+$"),
    "API identifier must be non-empty and contain only alphanumeric characters, '-' and '_'"
  )
}

object Api {
  def create(id: Api.Id): Api = {
    val now = Instant.now()
    Api(id = id, created = now, updated = now)
  }

  type Id = String

  final val ManageIdentity: Id = "manage-identity"
}
