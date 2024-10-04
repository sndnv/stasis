package stasis.identity.model.codes

import java.time.Instant

import stasis.identity.model.ChallengeMethod
import stasis.identity.model.clients.Client
import stasis.identity.model.codes.StoredAuthorizationCode.Challenge
import stasis.identity.model.owners.ResourceOwner

final case class StoredAuthorizationCode(
  code: AuthorizationCode,
  client: Client.Id,
  owner: ResourceOwner,
  scope: Option[String],
  challenge: Option[Challenge],
  created: Instant
)

object StoredAuthorizationCode {
  final case class Challenge(value: String, method: Option[ChallengeMethod])

  def apply(
    code: AuthorizationCode,
    client: Client.Id,
    owner: ResourceOwner,
    scope: Option[String]
  ): StoredAuthorizationCode = new StoredAuthorizationCode(
    code = code,
    client = client,
    owner = owner,
    scope = scope,
    challenge = None,
    created = Instant.now()
  )
}
