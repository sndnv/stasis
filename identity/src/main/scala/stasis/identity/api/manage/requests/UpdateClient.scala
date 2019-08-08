package stasis.identity.api.manage.requests

import stasis.identity.model.Seconds

final case class UpdateClient(
  tokenExpiration: Seconds,
  active: Boolean
) {
  require(tokenExpiration.value > 0, "token expiration must be a positive number")
}
