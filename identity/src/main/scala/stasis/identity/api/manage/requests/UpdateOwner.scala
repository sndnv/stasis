package stasis.identity.api.manage.requests

final case class UpdateOwner(
  allowedScopes: Seq[String],
  active: Boolean
) {
  require(allowedScopes.nonEmpty, "allowed scopes must not be empty")
}
