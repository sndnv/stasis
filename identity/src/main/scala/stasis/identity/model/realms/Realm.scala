package stasis.identity.model.realms

final case class Realm(
  id: Realm.Id,
  refreshTokensAllowed: Boolean
)

object Realm {
  type Id = String

  final val Master: Id = "master"
}
