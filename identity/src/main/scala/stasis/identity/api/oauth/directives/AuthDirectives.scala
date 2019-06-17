package stasis.identity.api.oauth.directives

import stasis.identity.api.oauth.setup.Providers
import stasis.identity.authentication.oauth.{ClientAuthenticator, ResourceOwnerAuthenticator}
import stasis.identity.model.apis.ApiStoreView
import stasis.identity.model.clients.ClientStoreView
import stasis.identity.model.codes.AuthorizationCodeStore
import stasis.identity.model.codes.generators.AuthorizationCodeGenerator
import stasis.identity.model.tokens.RefreshTokenStore
import stasis.identity.model.tokens.generators.{AccessTokenGenerator, RefreshTokenGenerator}

trait AuthDirectives
    extends AccessTokenGeneration
    with AudienceExtraction
    with AuthorizationCodeConsumption
    with AuthorizationCodeGeneration
    with ClientAuthentication
    with ClientRetrieval
    with RefreshTokenConsumption
    with RefreshTokenGeneration
    with ResourceOwnerAuthentication {

  protected def providers: Providers

  override protected def apiStore: ApiStoreView = providers.apiStore
  override protected def clientStore: ClientStoreView = providers.clientStore
  override protected def refreshTokenStore: RefreshTokenStore = providers.refreshTokenStore
  override protected def authorizationCodeStore: AuthorizationCodeStore = providers.authorizationCodeStore

  override protected def accessTokenGenerator: AccessTokenGenerator = providers.accessTokenGenerator
  override protected def authorizationCodeGenerator: AuthorizationCodeGenerator = providers.authorizationCodeGenerator

  override protected def clientAuthenticator: ClientAuthenticator = providers.clientAuthenticator
  override protected def refreshTokenGenerator: RefreshTokenGenerator = providers.refreshTokenGenerator
  override protected def resourceOwnerAuthenticator: ResourceOwnerAuthenticator = providers.resourceOwnerAuthenticator
}
