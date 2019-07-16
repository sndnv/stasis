package stasis.identity.model.realms

final case class Realm(
  id: Realm.Id,
  refreshTokensAllowed: Boolean
) {
  require(
    id.matches("^[a-zA-Z0-9\\-_]+$"),
    "Realm identifier must be non-empty and contain only alphanumeric characters, '-' and '_'"
  )
}

object Realm {
  type Id = String

  final val Master: Id = "master"
}
