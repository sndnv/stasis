package stasis.identity.api.manage.setup

import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.clients.ClientStore
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.identity.persistence.tokens.RefreshTokenStore

final case class Providers(
  apiStore: ApiStore,
  clientStore: ClientStore,
  codeStore: AuthorizationCodeStore,
  ownerStore: ResourceOwnerStore,
  tokenStore: RefreshTokenStore,
  ownerAuthenticator: ResourceOwnerAuthenticator
)
