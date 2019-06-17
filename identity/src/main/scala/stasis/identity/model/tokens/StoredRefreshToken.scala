package stasis.identity.model.tokens

import stasis.identity.model.owners.ResourceOwner

final case class StoredRefreshToken(
  token: RefreshToken,
  owner: ResourceOwner,
  scope: Option[String]
)
