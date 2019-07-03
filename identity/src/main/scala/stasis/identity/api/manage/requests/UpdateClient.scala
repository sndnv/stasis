package stasis.identity.api.manage.requests

import stasis.identity.model.Seconds

final case class UpdateClient(
  allowedScopes: Seq[String],
  tokenExpiration: Seconds,
  active: Boolean
) {
  require(allowedScopes.nonEmpty, "allowed scopes must not be empty")
  require(tokenExpiration.value > 0, "token expiration must be a positive number")
}
