package stasis.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import slick.jdbc.JdbcProfile

import stasis.core.persistence.backends.slick.SlickProfile
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.manifests.DefaultManifestStore
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.persistence.nodes.DefaultNodeStore
import stasis.core.persistence.nodes.NodeStore
import stasis.core.persistence.reservations.DefaultReservationStore
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.staging.StagingStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.persistence.migration.MigrationExecutor
import io.github.sndnv.layers.persistence.migration.MigrationResult
import io.github.sndnv.layers.service.PersistenceProvider
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.server.persistence.manifests.ServerManifestStore
import stasis.server.persistence.nodes.ServerNodeStore
import stasis.server.persistence.reservations.ServerReservationStore
import stasis.server.persistence.staging.ServerStagingStore
import stasis.server.security.Resource

class CorePersistence(
  persistenceConfig: typesafe.Config
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout
) extends PersistenceProvider { persistence =>
  private implicit val ec: ExecutionContext = system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")

  val reservationExpiration: FiniteDuration = persistenceConfig.getDuration("reservations.expiration").toMillis.millis

  val stagingStoreDescriptor: Option[CrateStore.Descriptor] =
    if (persistenceConfig.getBoolean("staging.enabled")) {
      Some(
        CrateStore.Descriptor(
          config = persistenceConfig.getConfig("staging.store")
        )
      )
    } else {
      None
    }

  val stagingStoreDestagingDelay: FiniteDuration = persistenceConfig.getDuration("staging.destaging-delay").toMillis.millis

  private val database: profile.backend.Database = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

  private val migrationExecutor: MigrationExecutor = MigrationExecutor()

  private val nodeStore = new DefaultNodeStore(
    name = "NODES",
    profile = profile,
    database = database,
    cache = MemoryStore(name = "nodes-cache")
  )

  val nodes: NodeStore = nodeStore

  val reservations: ReservationStore = DefaultReservationStore(
    name = "RESERVATIONS",
    expiration = reservationExpiration
  )

  val manifests: ManifestStore = new DefaultManifestStore(
    name = "MANIFESTS",
    profile = profile,
    database = database
  )

  val staging: Option[StagingStore] = stagingStoreDescriptor.map { descriptor =>
    StagingStore(
      crateStore = CrateStore.fromDescriptor(descriptor),
      destagingDelay = stagingStoreDestagingDelay
    )
  }

  def startup(): Future[Done] =
    nodeStore.prepare()

  override def migrate(): Future[MigrationResult] =
    for {
      manifestsMigration <- migrationExecutor.execute(forStore = manifests)
      nodesMigration <- migrationExecutor.execute(forStore = nodes)
      reservationsMigration <- migrationExecutor.execute(forStore = reservations)
    } yield {
      manifestsMigration + nodesMigration + reservationsMigration
    }

  override def init(): Future[Done] =
    for {
      _ <- manifests.init()
      _ <- nodes.init()
      _ <- reservations.init()
    } yield {
      Done
    }

  override def drop(): Future[Done] =
    for {
      _ <- manifests.drop()
      _ <- nodes.drop()
      _ <- reservations.drop()
    } yield {
      Done
    }

  def resources: Set[Resource] =
    Set(
      serverNodes.manage(),
      serverNodes.manageSelf(),
      serverNodes.view(),
      serverManifests.manage(),
      serverManifests.view(),
      serverReservations.view()
    ) ++ Set(
      serverStaging.map(_.manage()),
      serverStaging.map(_.view())
    ).flatten

  private val serverNodes: ServerNodeStore = ServerNodeStore(nodes)
  private val serverManifests: ServerManifestStore = ServerManifestStore(manifests)
  private val serverReservations: ServerReservationStore = ServerReservationStore(reservations)
  private val serverStaging: Option[ServerStagingStore] = staging.map(ServerStagingStore.apply)
}

object CorePersistence {
  def apply(
    persistenceConfig: typesafe.Config
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): CorePersistence =
    new CorePersistence(persistenceConfig)
}
