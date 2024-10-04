package stasis.identity.api.oauth.setup

import stasis.identity.authentication.oauth.ClientAuthenticator
import stasis.identity.authentication.oauth.ResourceOwnerAuthenticator
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.tokens.generators.AccessTokenGenerator
import stasis.identity.model.tokens.generators.RefreshTokenGenerator
import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.clients.ClientStore
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.identity.persistence.tokens.RefreshTokenStore

final case class Providers(
  apiStore: ApiStore.View,
  clientStore: ClientStore.View,
  resourceOwnerStore: ResourceOwnerStore.View,
  refreshTokenStore: RefreshTokenStore,
  authorizationCodeStore: AuthorizationCodeStore,
  accessTokenGenerator: AccessTokenGenerator,
  authorizationCodeGenerator: AuthorizationCodeGenerator,
  refreshTokenGenerator: RefreshTokenGenerator,
  clientAuthenticator: ClientAuthenticator,
  resourceOwnerAuthenticator: ResourceOwnerAuthenticator
)
