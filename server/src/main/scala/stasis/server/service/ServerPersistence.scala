package stasis.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import slick.jdbc.JdbcProfile

import stasis.core.persistence.backends.slick.SlickProfile
import stasis.core.persistence.commands.DefaultCommandStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.MigrationExecutor
import stasis.layers.persistence.migration.MigrationResult
import stasis.layers.service.PersistenceProvider
import stasis.layers.telemetry.TelemetryContext
import stasis.server.persistence.analytics.AnalyticsEntryStore
import stasis.server.persistence.analytics.DefaultAnalyticsEntryStore
import stasis.server.persistence.datasets.DatasetDefinitionStore
import stasis.server.persistence.datasets.DatasetEntryStore
import stasis.server.persistence.datasets.DefaultDatasetDefinitionStore
import stasis.server.persistence.datasets.DefaultDatasetEntryStore
import stasis.server.persistence.devices.DefaultDeviceBootstrapCodeStore
import stasis.server.persistence.devices.DefaultDeviceKeyStore
import stasis.server.persistence.devices.DefaultDeviceStore
import stasis.server.persistence.devices.DeviceBootstrapCodeStore
import stasis.server.persistence.devices.DeviceCommandStore
import stasis.server.persistence.devices.DeviceKeyStore
import stasis.server.persistence.devices.DeviceStore
import stasis.server.persistence.schedules.DefaultScheduleStore
import stasis.server.persistence.schedules.ScheduleStore
import stasis.server.persistence.users.DefaultUserStore
import stasis.server.persistence.users.UserStore
import stasis.server.security.Resource
import stasis.shared.model.devices.DeviceBootstrapCode

class ServerPersistence(
  persistenceConfig: typesafe.Config
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout
) extends PersistenceProvider {
  private implicit val ec: ExecutionContext = system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")

  val userSaltSize: Int = persistenceConfig.getInt("users.salt-size")

  private val database: profile.backend.Database = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

  private val migrationExecutor: MigrationExecutor = MigrationExecutor()

  val datasetDefinitions: DatasetDefinitionStore = new DefaultDatasetDefinitionStore(
    name = "DATASET_DEFINITIONS",
    profile = profile,
    database = database
  )

  val datasetEntries: DatasetEntryStore = new DefaultDatasetEntryStore(
    name = "DATASET_ENTRIES",
    profile = profile,
    database = database
  )

  val deviceBootstrapCodes: DeviceBootstrapCodeStore = new DefaultDeviceBootstrapCodeStore(
    name = "DEVICE_BOOTSTRAP_CODES",
    backend = backends.deviceBootstrapCodes
  )

  val devices: DeviceStore = new DefaultDeviceStore(
    name = "DEVICES",
    profile = profile,
    database = database
  )

  val deviceKeys: DeviceKeyStore = new DefaultDeviceKeyStore(
    name = "DEVICE_KEYS",
    profile = profile,
    database = database
  )

  val deviceCommands: DeviceCommandStore = DeviceCommandStore(
    store = new DefaultCommandStore(
      name = "DEVICE_COMMANDS",
      profile = profile,
      database = database
    )
  )

  val schedules: ScheduleStore = new DefaultScheduleStore(
    name = "SCHEDULES",
    profile = profile,
    database = database
  )

  val users: UserStore = new DefaultUserStore(
    name = "USERS",
    userSaltSize = userSaltSize,
    profile = profile,
    database = database
  )

  val analytics: AnalyticsEntryStore = new DefaultAnalyticsEntryStore(
    name = "ANALYTICS_ENTRIES",
    profile = profile,
    database = database
  )

  override def migrate(): Future[MigrationResult] =
    for {
      definitionsMigration <- migrationExecutor.execute(forStore = datasetDefinitions)
      entriesMigration <- migrationExecutor.execute(forStore = datasetEntries)
      deviceBootstrapCodesMigration <- migrationExecutor.execute(forStore = deviceBootstrapCodes)
      devicesMigration <- migrationExecutor.execute(forStore = devices)
      deviceKeysMigration <- migrationExecutor.execute(forStore = deviceKeys)
      commandsMigration <- migrationExecutor.execute(forStore = deviceCommands)
      schedulesMigration <- migrationExecutor.execute(forStore = schedules)
      usersMigration <- migrationExecutor.execute(forStore = users)
      analyticsMigration <- migrationExecutor.execute(forStore = analytics)
    } yield {
      (definitionsMigration
        + entriesMigration
        + deviceBootstrapCodesMigration
        + devicesMigration
        + deviceKeysMigration
        + schedulesMigration
        + usersMigration
        + commandsMigration
        + analyticsMigration)
    }

  override def init(): Future[Done] =
    for {
      _ <- datasetDefinitions.init()
      _ <- datasetEntries.init()
      _ <- deviceBootstrapCodes.init()
      _ <- devices.init()
      _ <- deviceKeys.init()
      _ <- deviceCommands.init()
      _ <- schedules.init()
      _ <- users.init()
      _ <- analytics.init()
    } yield {
      Done
    }

  override def drop(): Future[Done] =
    for {
      _ <- datasetDefinitions.drop()
      _ <- datasetEntries.drop()
      _ <- deviceBootstrapCodes.drop()
      _ <- devices.drop()
      _ <- deviceKeys.drop()
      _ <- deviceCommands.drop()
      _ <- schedules.drop()
      _ <- users.drop()
      _ <- analytics.drop()
    } yield {
      Done
    }

  def resources: Set[Resource] =
    Set(
      datasetDefinitions.manage(),
      datasetDefinitions.manageSelf(),
      datasetDefinitions.view(),
      datasetDefinitions.viewSelf(),
      datasetEntries.manage(),
      datasetEntries.manageSelf(),
      datasetEntries.view(),
      datasetEntries.viewSelf(),
      deviceBootstrapCodes.manage(),
      deviceBootstrapCodes.manageSelf(),
      deviceBootstrapCodes.view(),
      deviceBootstrapCodes.viewSelf(),
      devices.manage(),
      devices.manageSelf(),
      devices.view(),
      devices.viewSelf(),
      deviceKeys.manage(),
      deviceKeys.manageSelf(),
      deviceKeys.view(),
      deviceKeys.viewSelf(),
      deviceCommands.manage(),
      deviceCommands.manageSelf(),
      deviceCommands.view(),
      deviceCommands.viewSelf(),
      schedules.manage(),
      schedules.view(),
      schedules.viewPublic(),
      users.manage(),
      users.manageSelf(),
      users.view(),
      users.viewSelf(),
      analytics.manage(),
      analytics.manageSelf(),
      analytics.view()
    )

  private object backends {
    val deviceBootstrapCodes: MemoryStore[String, DeviceBootstrapCode] =
      MemoryStore(name = "device-bootstrap-code-store")
  }
}

object ServerPersistence {
  def apply(
    persistenceConfig: typesafe.Config
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): ServerPersistence =
    new ServerPersistence(persistenceConfig)
}
