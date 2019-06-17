package stasis.test.specs.unit.identity.api.manage

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.identity.api.manage.setup.Providers
import stasis.identity.model.apis.{Api, ApiStore}
import stasis.identity.model.clients.{Client, ClientStore}
import stasis.identity.model.codes.{AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore}
import stasis.identity.model.realms.{Realm, RealmStore}
import stasis.identity.model.tokens.{RefreshTokenStore, StoredRefreshToken}
import stasis.test.specs.unit.identity.RouteTest
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.Future
import scala.concurrent.duration._

trait ManageFixtures { _: RouteTest =>
  def createManageProviders(expiration: FiniteDuration = 3.seconds) = Providers(
    apiStore = ApiStore(
      MemoryBackend[Api.Id, Api](name = s"api-store-${java.util.UUID.randomUUID()}")
    ),
    clientStore = ClientStore(
      MemoryBackend[Client.Id, Client](name = s"client-store-${java.util.UUID.randomUUID()}")
    ),
    codeStore = AuthorizationCodeStore(
      expiration = expiration,
      MemoryBackend[Client.Id, StoredAuthorizationCode](name = s"code-store-${java.util.UUID.randomUUID()}")
    ),
    ownerStore = ResourceOwnerStore(
      MemoryBackend[ResourceOwner.Id, ResourceOwner](name = s"owner-store-${java.util.UUID.randomUUID()}")
    ),
    realmStore = RealmStore(
      MemoryBackend[Realm.Id, Realm](name = s"realm-store-${java.util.UUID.randomUUID()}")
    ),
    tokenStore = RefreshTokenStore(
      expiration = expiration,
      MemoryBackend[Client.Id, StoredRefreshToken](name = s"token-store-${java.util.UUID.randomUUID()}")
    ),
    ownerAuthenticator =
      (_: OAuth2BearerToken) => Future.successful(Generators.generateResourceOwner.copy(realm = Realm.Master))
  )
}
