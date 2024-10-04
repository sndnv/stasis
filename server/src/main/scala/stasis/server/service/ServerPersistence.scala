package stasis.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import slick.jdbc.JdbcProfile

import stasis.core.persistence.backends.slick.SlickBackend
import stasis.core.persistence.backends.slick.SlickProfile
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.MigrationExecutor
import stasis.layers.persistence.migration.MigrationResult
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.datasets._
import stasis.server.model.devices.DeviceBootstrapCodeStore
import stasis.server.model.devices.DeviceKeyStore
import stasis.server.model.devices.DeviceKeyStoreSerdes
import stasis.server.model.devices.DeviceStore
import stasis.server.model.devices.DeviceStoreSerdes
import stasis.server.model.schedules.ScheduleStore
import stasis.server.model.schedules.ScheduleStoreSerdes
import stasis.server.model.users.UserStore
import stasis.server.model.users.UserStoreSerdes
import stasis.server.security.Resource
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceBootstrapCode
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

class ServerPersistence(
  persistenceConfig: typesafe.Config
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout
) {
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

  val datasetDefinitions: DatasetDefinitionStore = DatasetDefinitionStore(backend = backends.definitions)

  val datasetEntries: DatasetEntryStore = DatasetEntryStore(backend = backends.entries)

  val deviceBootstrapCodes: DeviceBootstrapCodeStore = DeviceBootstrapCodeStore(backend = backends.deviceBootstrapCodes)

  val devices: DeviceStore = DeviceStore(backend = backends.devices)

  val deviceKeys: DeviceKeyStore = DeviceKeyStore(backend = backends.deviceKeys)

  val schedules: ScheduleStore = ScheduleStore(backend = backends.schedules)

  val users: UserStore = UserStore(
    userSaltSize = userSaltSize,
    backend = backends.users
  )

  def migrate(): Future[MigrationResult] =
    for {
      definitionsMigration <- migrationExecutor.execute(forStore = backends.definitions)
      entriesMigration <- migrationExecutor.execute(forStore = backends.entries)
      deviceBootstrapCodesMigration <- migrationExecutor.execute(forStore = backends.deviceBootstrapCodes)
      devicesMigration <- migrationExecutor.execute(forStore = backends.devices)
      deviceKeysMigration <- migrationExecutor.execute(forStore = backends.deviceKeys)
      schedulesMigration <- migrationExecutor.execute(forStore = backends.schedules)
      usersMigration <- migrationExecutor.execute(forStore = backends.users)
    } yield {
      (definitionsMigration
        + entriesMigration
        + deviceBootstrapCodesMigration
        + devicesMigration
        + deviceKeysMigration
        + schedulesMigration
        + usersMigration)
    }

  def init(): Future[Done] =
    for {
      _ <- backends.definitions.init()
      _ <- backends.entries.init()
      _ <- backends.deviceBootstrapCodes.init()
      _ <- backends.devices.init()
      _ <- backends.deviceKeys.init()
      _ <- backends.schedules.init()
      _ <- backends.users.init()
    } yield {
      Done
    }

  def drop(): Future[Done] =
    for {
      _ <- backends.definitions.drop()
      _ <- backends.entries.drop()
      _ <- backends.deviceBootstrapCodes.drop()
      _ <- backends.devices.drop()
      _ <- backends.deviceKeys.drop()
      _ <- backends.schedules.drop()
      _ <- backends.users.drop()
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
      schedules.manage(),
      schedules.view(),
      schedules.viewPublic(),
      users.manage(),
      users.manageSelf(),
      users.view(),
      users.viewSelf()
    )

  private object backends {
    val definitions: SlickBackend[DatasetDefinition.Id, DatasetDefinition] = SlickBackend(
      name = "DATASET_DEFINITIONS",
      profile = profile,
      database = database,
      serdes = DatasetDefinitionStoreSerdes
    )

    val entries: SlickBackend[DatasetEntry.Id, DatasetEntry] = SlickBackend(
      name = "DATASET_ENTRIES",
      profile = profile,
      database = database,
      serdes = DatasetEntryStoreSerdes
    )

    val deviceBootstrapCodes: MemoryStore[String, DeviceBootstrapCode] =
      MemoryStore(name = "device-bootstrap-code-store")

    val devices: SlickBackend[Device.Id, Device] = SlickBackend(
      name = "DEVICES",
      profile = profile,
      database = database,
      serdes = DeviceStoreSerdes
    )

    val deviceKeys: SlickBackend[Device.Id, DeviceKey] = SlickBackend(
      name = "DEVICE_KEYS",
      profile = profile,
      database = database,
      serdes = DeviceKeyStoreSerdes
    )

    val schedules: SlickBackend[Schedule.Id, Schedule] = SlickBackend(
      name = "SCHEDULES",
      profile = profile,
      database = database,
      serdes = ScheduleStoreSerdes
    )

    val users: SlickBackend[User.Id, User] = SlickBackend(
      name = "USERS",
      profile = profile,
      database = database,
      serdes = UserStoreSerdes
    )
  }
}

object ServerPersistence {
  def apply(
    persistenceConfig: typesafe.Config
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): ServerPersistence =
    new ServerPersistence(persistenceConfig)
}
