package stasis.identity.api.manage

import scala.concurrent.Future

import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken

import stasis.identity.RouteTest
import stasis.identity.api.manage.setup.Providers
import stasis.identity.model.Generators
import stasis.identity.persistence.mocks.MockApiStore
import stasis.identity.persistence.mocks.MockAuthorizationCodeStore
import stasis.identity.persistence.mocks.MockClientStore
import stasis.identity.persistence.mocks.MockRefreshTokenStore
import stasis.identity.persistence.mocks.MockResourceOwnerStore
import io.github.sndnv.layers

trait ManageFixtures { _: RouteTest =>
  def createManageProviders(
    withOwnerScopes: Seq[String] = layers.testing.Generators.generateSeq(
      min = 1,
      g = layers.testing.Generators.generateString(withSize = 10)
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
