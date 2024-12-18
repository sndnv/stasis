package stasis.identity.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import slick.jdbc.JdbcProfile

import stasis.core.persistence.backends.slick.SlickProfile
import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.persistence.apis.ApiStore
import stasis.identity.persistence.apis.DefaultApiStore
import stasis.identity.persistence.clients.ClientStore
import stasis.identity.persistence.clients.DefaultClientStore
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.identity.persistence.codes.DefaultAuthorizationCodeStore
import stasis.identity.persistence.owners.DefaultResourceOwnerStore
import stasis.identity.persistence.owners.ResourceOwnerStore
import stasis.identity.persistence.tokens.DefaultRefreshTokenStore
import stasis.identity.persistence.tokens.RefreshTokenStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.MigrationExecutor
import stasis.layers.persistence.migration.MigrationResult
import stasis.layers.service.PersistenceProvider
import stasis.layers.telemetry.TelemetryContext

class Persistence(
  persistenceConfig: typesafe.Config,
  authorizationCodeExpiration: FiniteDuration,
  refreshTokenExpiration: FiniteDuration
)(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout)
    extends PersistenceProvider {
  private implicit val ec: ExecutionContext = system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")

  private val database: profile.backend.Database = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

  private val migrationExecutor: MigrationExecutor = MigrationExecutor()

  val apis: ApiStore =
    new DefaultApiStore(
      name = "APIS",
      profile = profile,
      database = database
    )

  val clients: ClientStore =
    new DefaultClientStore(
      name = "CLIENTS",
      profile = profile,
      database = database
    )

  val resourceOwners: ResourceOwnerStore =
    new DefaultResourceOwnerStore(
      name = "RESOURCE_OWNERS",
      profile = profile,
      database = database
    )

  val refreshTokens: RefreshTokenStore =
    new DefaultRefreshTokenStore(
      name = "REFRESH_TOKENS",
      profile = profile,
      database = database,
      expiration = refreshTokenExpiration
    )

  val authorizationCodes: AuthorizationCodeStore =
    new DefaultAuthorizationCodeStore(
      name = "AUTHORIZATION_CODES",
      expiration = authorizationCodeExpiration,
      backend = backends.codes
    )

  override def migrate(): Future[MigrationResult] =
    for {
      apisMigration <- migrationExecutor.execute(forStore = apis)
      clientsMigration <- migrationExecutor.execute(forStore = clients)
      resourceOwnersMigration <- migrationExecutor.execute(forStore = resourceOwners)
      refreshTokensMigration <- migrationExecutor.execute(forStore = refreshTokens)
      authorizationCodesMigration <- migrationExecutor.execute(forStore = authorizationCodes)
    } yield {
      apisMigration + clientsMigration + resourceOwnersMigration + refreshTokensMigration + authorizationCodesMigration
    }

  override def init(): Future[Done] =
    for {
      _ <- apis.init()
      _ <- clients.init()
      _ <- resourceOwners.init()
      _ <- refreshTokens.init()
      _ <- authorizationCodes.init()
    } yield {
      Done
    }

  override def drop(): Future[Done] =
    for {
      _ <- apis.drop()
      _ <- clients.drop()
      _ <- resourceOwners.drop()
      _ <- refreshTokens.drop()
      _ <- authorizationCodes.drop()
    } yield {
      Done
    }

  private object backends {
    val codes: KeyValueStore[AuthorizationCode, StoredAuthorizationCode] =
      MemoryStore[AuthorizationCode, StoredAuthorizationCode](name = "code-store")
  }
}

object Persistence {
  def apply(
    persistenceConfig: typesafe.Config,
    authorizationCodeExpiration: FiniteDuration,
    refreshTokenExpiration: FiniteDuration
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): Persistence =
    new Persistence(
      persistenceConfig = persistenceConfig,
      authorizationCodeExpiration = authorizationCodeExpiration,
      refreshTokenExpiration = refreshTokenExpiration
    )
}
