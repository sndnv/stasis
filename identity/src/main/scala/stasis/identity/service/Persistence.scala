package stasis.identity.service

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import slick.jdbc.JdbcProfile
import stasis.core.persistence.backends.KeyValueBackend
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.persistence.backends.slick.{SlickBackend, SlickProfile}
import stasis.identity.model.apis.{ApiStore, ApiStoreSerdes}
import stasis.identity.model.clients.{Client, ClientStore, ClientStoreSerdes}
import stasis.identity.model.codes.{AuthorizationCode, AuthorizationCodeStore, StoredAuthorizationCode}
import stasis.identity.model.owners.{ResourceOwner, ResourceOwnerStore, ResourceOwnerStoreSerdes}
import stasis.identity.model.tokens.{RefreshToken, RefreshTokenStore, RefreshTokenStoreSerdes, StoredRefreshToken}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class Persistence(
  persistenceConfig: typesafe.Config,
  authorizationCodeExpiration: FiniteDuration,
  refreshTokenExpiration: FiniteDuration
)(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout) {
  private implicit val ec: ExecutionContext = system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")

  private val database: profile.backend.DatabaseDef = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

  val apis: ApiStore =
    ApiStore(backend = backends.apis)

  val clients: ClientStore =
    ClientStore(backend = backends.clients)

  val resourceOwners: ResourceOwnerStore =
    ResourceOwnerStore(backend = backends.owners)

  val refreshTokens: RefreshTokenStore =
    RefreshTokenStore(
      expiration = refreshTokenExpiration,
      backend = backends.tokens,
      directory = backends.tokenDirectory
    )

  val authorizationCodes: AuthorizationCodeStore =
    AuthorizationCodeStore(
      expiration = authorizationCodeExpiration,
      backend = backends.codes
    )

  def init(): Future[Done] =
    for {
      _ <- backends.apis.init()
      _ <- backends.clients.init()
      _ <- backends.owners.init()
      _ <- backends.tokens.init()
      _ <- backends.codes.init()
    } yield {
      Done
    }

  def drop(): Future[Done] =
    for {
      _ <- backends.apis.drop()
      _ <- backends.clients.drop()
      _ <- backends.owners.drop()
      _ <- backends.tokens.drop()
      _ <- backends.codes.drop()
    } yield {
      Done
    }

  private object backends {
    val apis = new SlickBackend(
      tableName = "APIS",
      profile = profile,
      database = database,
      serdes = ApiStoreSerdes
    )

    val clients: KeyValueBackend[Client.Id, Client] = new SlickBackend(
      tableName = "CLIENTS",
      profile = profile,
      database = database,
      serdes = ClientStoreSerdes
    )

    val owners: KeyValueBackend[ResourceOwner.Id, ResourceOwner] = new SlickBackend(
      tableName = "RESOURCE_OWNERS",
      profile = profile,
      database = database,
      serdes = ResourceOwnerStoreSerdes
    )

    val tokens: KeyValueBackend[RefreshToken, StoredRefreshToken] = new SlickBackend(
      tableName = "REFRESH_TOKENS",
      profile = profile,
      database = database,
      serdes = RefreshTokenStoreSerdes
    )

    val tokenDirectory: KeyValueBackend[(Client.Id, ResourceOwner.Id), RefreshToken] =
      MemoryBackend[(Client.Id, ResourceOwner.Id), RefreshToken](
        name = s"token-directory-${java.util.UUID.randomUUID()}"
      )

    val codes: KeyValueBackend[AuthorizationCode, StoredAuthorizationCode] =
      MemoryBackend[AuthorizationCode, StoredAuthorizationCode](
        name = s"code-store-${java.util.UUID.randomUUID()}"
      )
  }
}
