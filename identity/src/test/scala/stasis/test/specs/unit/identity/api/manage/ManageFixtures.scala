package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.api.manage.setup.Providers
import stasis.identity.model.apis.{Api, ApiStore}
import stasis.identity.model.clients.{Client, ClientStore}
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore, StoredRefreshToken}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._

trait ManageFixtures { _: RouteTest =>
  def createManageProviders(
    expiration: FiniteDuration = 3.seconds,
    withOwnerScopes: Seq[String] = Generators.generateSeq(min = 1, g = Generators.generateString(withSize = 10))
  ) = Providers(
    apiStore = ApiStore(
      MemoryBackend[Api.Id, Api](name = s"api-store-${java.util.UUID.randomUUID()}")
    ),
    clientStore = ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    ),
    codeStore = AuthorizationCodeStore(
      expiration = expiration,
      MemoryBackend[AuthorizationCode, StoredAuthorizationCode](name = s"code-store-${java.util.UUID.randomUUID()}")
    ),
    ownerStore = ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    ),
    tokenStore = RefreshTokenStore(
      expiration = expiration,
      MemoryBackend[RefreshToken, StoredRefreshToken](name = s"token-store-${java.util.UUID.randomUUID()}")
    ),
    ownerAuthenticator = (_: OAuth2BearerToken) =>
      Future.successful(Generators.generateResourceOwner.copy(allowedScopes = withOwnerScopes))
  )
}
