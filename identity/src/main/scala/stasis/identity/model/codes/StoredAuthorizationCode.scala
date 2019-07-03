package stasis.identity.model.codes

import stasis.identity.model.owners.ResourceOwner

final case class StoredAuthorizationCode(
  code: AuthorizationCode,
  owner: ResourceOwner,
  scope: Option[String]
)
