package stasis.test.specs.unit.identity.api.oauth

import org.jose4j.jwk.JsonWebKey
import stasis.identity.api.oauth.setup.Providers
import stasis.identity.authentication.oauth.{DefaultClientAuthenticator, DefaultResourceOwnerAuthenticator}
import stasis.identity.model.apis.ApiStore
import stasis.identity.model.clients.ClientStore
import stasis.identity.model.codes.AuthorizationCodeStore
import stasis.identity.model.codes.generators.DefaultAuthorizationCodeGenerator
import stasis.identity.model.owners.ResourceOwnerStore
import stasis.identity.model.realms.RealmStore
import stasis.identity.model.secrets.Secret
import stasis.identity.model.tokens.RefreshTokenStore
import stasis.identity.model.tokens.generators.{JwtBearerAccessTokenGenerator, RandomRefreshTokenGenerator}
import stasis.test.specs.unit.core.security.jwt.mocks.MockJwksGenerators
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.api.oauth.OAuthFixtures.{TestSecretConfig, TestStores}
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

trait OAuthFixtures { _: RouteTest =>
  def createOAuthFixtures(
    stores: TestStores = TestStores(
      apis = createApiStore(),
      clients = createClientStore(),
      realms = createRealmStore(),
      tokens = createTokenStore(),
      codes = createCodeStore(),
      owners = createOwnerStore()
    ),
    jwk: JsonWebKey = MockJwksGenerators.generateRandomRsaKey(
      keyId = Some(Generators.generateString(withSize = 16))
    ),
    testSecretConfig: TestSecretConfig = TestSecretConfig()
  ): (TestStores, TestSecretConfig, Providers) = (
    stores,
    testSecretConfig,
    Providers(
      apiStore = stores.apis.view,
      clientStore = stores.clients.view,
      realmStore = stores.realms.view,
      refreshTokenStore = stores.tokens,
      authorizationCodeStore = stores.codes,
      accessTokenGenerator = new JwtBearerAccessTokenGenerator(
        issuer = "test-issuer",
        jwk = jwk,
        jwtExpiration = 30.seconds
      ),
      authorizationCodeGenerator = new DefaultAuthorizationCodeGenerator(codeSize = 16),
      refreshTokenGenerator = new RandomRefreshTokenGenerator(tokenSize = 16),
      clientAuthenticator = new DefaultClientAuthenticator(stores.clients.view, testSecretConfig.client),
      resourceOwnerAuthenticator = new DefaultResourceOwnerAuthenticator(stores.owners.view, testSecretConfig.owner)
    )
  )
}

object OAuthFixtures {
  final case class TestStores(
    apis: ApiStore,
    clients: ClientStore,
    realms: RealmStore,
    tokens: RefreshTokenStore,
    codes: AuthorizationCodeStore,
    owners: ResourceOwnerStore
  )

  final case class TestSecretConfig(
    client: Secret.ClientConfig = Secret.ClientConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 20.millis
    ),
    owner: Secret.ResourceOwnerConfig = Secret.ResourceOwnerConfig(
      algorithm = "PBKDF2WithHmacSHA512",
      iterations = 10000,
      derivedKeySize = 32,
      saltSize = 16,
      authenticationDelay = 20.millis
    )
  )
}
