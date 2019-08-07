package stasis.identity.model.tokens

import java.time.Instant

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner

final case class StoredRefreshToken(
  token: RefreshToken,
  client: Client.Id,
  owner: ResourceOwner,
  scope: Option[String],
  expiration: Instant
)
