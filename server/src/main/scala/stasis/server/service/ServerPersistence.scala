package stasis.server.service

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import com.typesafe.{config => typesafe}
import slick.jdbc.JdbcProfile
import stasis.core.persistence.backends.slick.{SlickBackend, SlickProfile}
import stasis.server.model.datasets._
import stasis.server.model.devices.{DeviceStore, DeviceStoreSerdes}
import stasis.server.model.schedules.{ScheduleStore, ScheduleStoreSerdes}
import stasis.server.model.users.{UserStore, UserStoreSerdes}
import stasis.server.security.Resource
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.shared.model.users.User

import scala.concurrent.{ExecutionContext, Future}

class ServerPersistence(
  persistenceConfig: typesafe.Config
)(implicit system: ActorSystem[SpawnProtocol.Command]) {
  private implicit val ec: ExecutionContext = system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")

  val userSaltSize: Int = persistenceConfig.getInt("users.salt-size")

  private val database: profile.backend.DatabaseDef = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

  val datasetDefinitions: DatasetDefinitionStore = DatasetDefinitionStore(backend = backends.definitions)

  val datasetEntries: DatasetEntryStore = DatasetEntryStore(backend = backends.entries)

  val devices: DeviceStore = DeviceStore(backend = backends.devices)

  val schedules: ScheduleStore = ScheduleStore(backend = backends.schedules)

  val users: UserStore = UserStore(
    userSaltSize = userSaltSize,
    backend = backends.users
  )

  def init(): Future[Done] =
    for {
      _ <- backends.definitions.init()
      _ <- backends.entries.init()
      _ <- backends.devices.init()
      _ <- backends.schedules.init()
      _ <- backends.users.init()
    } yield {
      Done
    }

  def drop(): Future[Done] =
    for {
      _ <- backends.definitions.drop()
      _ <- backends.entries.drop()
      _ <- backends.devices.drop()
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
      devices.manage(),
      devices.manageSelf(),
      devices.view(),
      devices.viewSelf(),
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
      tableName = "DATASET_DEFINITIONS",
      profile = profile,
      database = database,
      serdes = DatasetDefinitionStoreSerdes
    )

    val entries: SlickBackend[DatasetEntry.Id, DatasetEntry] = SlickBackend(
      tableName = "DATASET_ENTRIES",
      profile = profile,
      database = database,
      serdes = DatasetEntryStoreSerdes
    )

    val devices: SlickBackend[Device.Id, Device] = SlickBackend(
      tableName = "DEVICES",
      profile = profile,
      database = database,
      serdes = DeviceStoreSerdes
    )

    val schedules: SlickBackend[Schedule.Id, Schedule] = SlickBackend(
      tableName = "SCHEDULES",
      profile = profile,
      database = database,
      serdes = ScheduleStoreSerdes
    )

    val users: SlickBackend[User.Id, User] = SlickBackend(
      tableName = "USERS",
      profile = profile,
      database = database,
      serdes = UserStoreSerdes
    )
  }
}

object ServerPersistence {
  def apply(
    persistenceConfig: typesafe.Config
  )(implicit system: ActorSystem[SpawnProtocol.Command]): ServerPersistence =
    new ServerPersistence(persistenceConfig)
}
