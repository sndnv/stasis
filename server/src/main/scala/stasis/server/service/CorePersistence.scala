package stasis.server.service

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import com.typesafe.{config => typesafe}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout
import slick.jdbc.JdbcProfile

import stasis.core.packaging.Crate
import stasis.core.packaging.Manifest
import stasis.core.persistence.CrateStorageReservation
import stasis.core.persistence.StoreInitializationResult
import stasis.core.persistence.backends.slick.SlickBackend
import stasis.core.persistence.backends.slick.SlickProfile
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.manifests.ManifestStore
import stasis.core.persistence.manifests.ManifestStoreSerdes
import stasis.core.persistence.nodes.NodeStore
import stasis.core.persistence.nodes.NodeStoreSerdes
import stasis.core.persistence.reservations.ReservationStore
import stasis.core.persistence.reservations.ReservationStoreSerdes
import stasis.core.persistence.staging.StagingStore
import stasis.core.routing.Node
import stasis.layers.persistence.migration.MigrationExecutor
import stasis.layers.persistence.migration.MigrationResult
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.manifests.ServerManifestStore
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.model.reservations.ServerReservationStore
import stasis.server.model.staging.ServerStagingStore
import stasis.server.security.Resource

class CorePersistence(
  persistenceConfig: typesafe.Config
)(implicit
  system: ActorSystem[Nothing],
  telemetry: TelemetryContext,
  timeout: Timeout
) { persistence =>
  private implicit val ec: ExecutionContext = system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")

  val nodeCachingEnabled: Boolean = persistenceConfig.getBoolean("nodes.caching-enabled")

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

  private val StoreInitializationResult(nodeStore, nodeStoreInit) = NodeStore(
    backend = backends.nodes,
    cachingEnabled = nodeCachingEnabled
  )

  private val StoreInitializationResult(reservationStore, reservationStoreInit) = ReservationStore(
    expiration = reservationExpiration,
    backend = backends.reservations
  )

  val manifests: ManifestStore = ManifestStore(backend = backends.manifests)

  val nodes: NodeStore = nodeStore

  val reservations: ReservationStore = reservationStore

  val staging: Option[StagingStore] = stagingStoreDescriptor.map { descriptor =>
    StagingStore(
      crateStore = CrateStore.fromDescriptor(descriptor),
      destagingDelay = stagingStoreDestagingDelay
    )
  }

  def startup(): Future[Done] =
    for {
      _ <- nodeStoreInit()
      _ <- reservationStoreInit()
    } yield {
      Done
    }

  def migrate(): Future[MigrationResult] =
    for {
      manifestsMigration <- migrationExecutor.execute(forStore = backends.manifests)
      nodesMigration <- migrationExecutor.execute(forStore = backends.nodes)
      reservationsMigration <- migrationExecutor.execute(forStore = backends.reservations)
    } yield {
      manifestsMigration + nodesMigration + reservationsMigration
    }

  def init(): Future[Done] =
    for {
      _ <- backends.manifests.init()
      _ <- backends.nodes.init()
      _ <- backends.reservations.init()
    } yield {
      Done
    }

  def drop(): Future[Done] =
    for {
      _ <- backends.manifests.drop()
      _ <- backends.nodes.drop()
      _ <- backends.reservations.drop()
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

  private object backends {
    val manifests: SlickBackend[Crate.Id, Manifest] = SlickBackend(
      name = "MANIFESTS",
      profile = profile,
      database = database,
      serdes = ManifestStoreSerdes
    )

    val nodes: SlickBackend[Node.Id, Node] = SlickBackend(
      name = "NODES",
      profile = profile,
      database = database,
      serdes = NodeStoreSerdes
    )

    val reservations: SlickBackend[CrateStorageReservation.Id, CrateStorageReservation] = SlickBackend(
      name = "RESERVATIONS",
      profile = profile,
      database = database,
      serdes = ReservationStoreSerdes
    )
  }
}

object CorePersistence {
  def apply(
    persistenceConfig: typesafe.Config
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, timeout: Timeout): CorePersistence =
    new CorePersistence(persistenceConfig)
}
