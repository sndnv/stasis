package stasis.identity.api.manage.setup

import stasis.identity.authentication.manage.ResourceOwnerAuthenticator
import stasis.identity.model.apis.ApiStore
import stasis.identity.model.clients.ClientStore
import stasis.identity.model.codes.AuthorizationCodeStore
import stasis.identity.model.owners.ResourceOwnerStore
import stasis.identity.model.realms.RealmStore
import stasis.identity.model.tokens.RefreshTokenStore

final case class Providers(
  apiStore: ApiStore,
  clientStore: ClientStore,
  codeStore: AuthorizationCodeStore,
  ownerStore: ResourceOwnerStore,
  realmStore: RealmStore,
  tokenStore: RefreshTokenStore,
  ownerAuthenticator: ResourceOwnerAuthenticator
)
