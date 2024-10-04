package stasis.identity.model.tokens

import java.time.Instant

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner

final case class StoredRefreshToken(
  token: RefreshToken,
  client: Client.Id,
  owner: ResourceOwner.Id,
  scope: Option[String],
  expiration: Instant,
  created: Instant
)
