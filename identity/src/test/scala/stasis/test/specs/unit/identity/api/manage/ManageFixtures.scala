package stasis.test.specs.unit.identity.api.manage

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.identity.api.manage.setup.Providers
import stasis.layers
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators
import stasis.test.specs.unit.identity.persistence.mocks.MockApiStore
import stasis.test.specs.unit.identity.persistence.mocks.MockAuthorizationCodeStore
import stasis.test.specs.unit.identity.persistence.mocks.MockClientStore
import stasis.test.specs.unit.identity.persistence.mocks.MockRefreshTokenStore
import stasis.test.specs.unit.identity.persistence.mocks.MockResourceOwnerStore

trait ManageFixtures { _: RouteTest =>
  def createManageProviders(
    withOwnerScopes: Seq[String] = layers.Generators.generateSeq(
      min = 1,
      g = layers.Generators.generateString(withSize = 10)
    )
  ): Providers =
    Providers(
      apiStore = MockApiStore(),
      clientStore = MockClientStore(),
      codeStore = MockAuthorizationCodeStore(),
      ownerStore = MockResourceOwnerStore(),
      tokenStore = MockRefreshTokenStore(),
      ownerAuthenticator =
        (_: OAuth2BearerToken) => Future.successful(Generators.generateResourceOwner.copy(allowedScopes = withOwnerScopes))
    )
}
