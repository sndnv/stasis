package stasis.identity.api.oauth.directives

import stasis.identity.api.oauth.setup.Config
import stasis.identity.api.oauth.setup.Providers
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
  protected def config: Config

  override protected def realm: String = config.realm
  override protected def refreshTokensAllowed: Boolean = config.refreshTokensAllowed

  override protected def apiStore: ApiStore.View = providers.apiStore
  override protected def clientStore: ClientStore.View = providers.clientStore
  override protected def resourceOwnerStore: ResourceOwnerStore.View = providers.resourceOwnerStore
  override protected def refreshTokenStore: RefreshTokenStore = providers.refreshTokenStore
  override protected def authorizationCodeStore: AuthorizationCodeStore = providers.authorizationCodeStore

  override protected def accessTokenGenerator: AccessTokenGenerator = providers.accessTokenGenerator
  override protected def authorizationCodeGenerator: AuthorizationCodeGenerator = providers.authorizationCodeGenerator

  override protected def clientAuthenticator: ClientAuthenticator = providers.clientAuthenticator
  override protected def refreshTokenGenerator: RefreshTokenGenerator = providers.refreshTokenGenerator
  override protected def resourceOwnerAuthenticator: ResourceOwnerAuthenticator = providers.resourceOwnerAuthenticator
}
