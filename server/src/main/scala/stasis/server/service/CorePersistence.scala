package stasis.server.service

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import slick.jdbc.JdbcProfile
import stasis.core.packaging.{Crate, Manifest}
import stasis.core.persistence.backends.slick.{SlickBackend, SlickProfile}
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.manifests.{ManifestStore, ManifestStoreSerdes}
import stasis.core.persistence.nodes.{NodeStore, NodeStoreSerdes}
import stasis.core.persistence.reservations.{ReservationStore, ReservationStoreSerdes}
import stasis.core.persistence.staging.StagingStore
import stasis.core.persistence.{CrateStorageReservation, StoreInitializationResult}
import stasis.core.routing.Node
import stasis.server.model.nodes.ServerNodeStore
import stasis.server.model.reservations.ServerReservationStore
import stasis.server.model.staging.ServerStagingStore
import stasis.server.security.Resource

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class CorePersistence(
  persistenceConfig: typesafe.Config
)(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout) { persistence =>
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

  private val database: profile.backend.DatabaseDef = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

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
      serverNodes.view(),
      serverReservations.view()
    ) ++ Set(
      serverStaging.map(_.manage()),
      serverStaging.map(_.view())
    ).flatten

  private val serverNodes: ServerNodeStore = ServerNodeStore(nodes)
  private val serverReservations: ServerReservationStore = ServerReservationStore(reservations)
  private val serverStaging: Option[ServerStagingStore] = staging.map(ServerStagingStore.apply)

  private object backends {
    val manifests: SlickBackend[Crate.Id, Manifest] = SlickBackend(
      tableName = "MANIFESTS",
      profile = profile,
      database = database,
      serdes = ManifestStoreSerdes
    )

    val nodes: SlickBackend[Node.Id, Node] = SlickBackend(
      tableName = "NODES",
      profile = profile,
      database = database,
      serdes = NodeStoreSerdes
    )

    val reservations: SlickBackend[CrateStorageReservation.Id, CrateStorageReservation] = SlickBackend(
      tableName = "RESERVATIONS",
      profile = profile,
      database = database,
      serdes = ReservationStoreSerdes
    )
  }
}

object CorePersistence {
  def apply(
    persistenceConfig: typesafe.Config
  )(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): CorePersistence =
    new CorePersistence(persistenceConfig)
}
