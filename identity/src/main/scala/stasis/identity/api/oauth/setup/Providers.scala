package stasis.identity.api.oauth.setup

import stasis.identity.authentication.oauth.{ClientAuthenticator, ResourceOwnerAuthenticator}
import stasis.identity.model.apis.ApiStoreView
import stasis.identity.model.clients.ClientStoreView
import stasis.identity.model.codes.AuthorizationCodeStore
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.realms.RealmStoreView
import stasis.identity.model.tokens.RefreshTokenStore
import stasis.identity.model.tokens.generators.{AccessTokenGenerator, RefreshTokenGenerator}

final case class Providers(
  apiStore: ApiStoreView,
  clientStore: ClientStoreView,
  realmStore: RealmStoreView,
  refreshTokenStore: RefreshTokenStore,
  authorizationCodeStore: AuthorizationCodeStore,
  accessTokenGenerator: AccessTokenGenerator,
  authorizationCodeGenerator: AuthorizationCodeGenerator,
  refreshTokenGenerator: RefreshTokenGenerator,
  clientAuthenticator: ClientAuthenticator,
  resourceOwnerAuthenticator: ResourceOwnerAuthenticator
)
