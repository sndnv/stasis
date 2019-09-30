package stasis.server.service

import akka.Done
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import com.typesafe.{config => typesafe}
import slick.jdbc.{H2Profile, JdbcProfile}
import stasis.core.persistence.StoreInitializationResult
import stasis.core.persistence.backends.slick.SlickBackend
import stasis.core.persistence.crates.CrateStore
import stasis.core.persistence.manifests.{ManifestStore, ManifestStoreSerdes}
import stasis.core.persistence.nodes.{NodeStore, NodeStoreSerdes}
import stasis.core.persistence.reservations.{ReservationStore, ReservationStoreSerdes}
import stasis.core.persistence.staging.StagingStore

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class CorePersistence(
  persistenceConfig: typesafe.Config,
)(implicit system: ActorSystem[SpawnProtocol], timeout: Timeout) {
  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val untyped: akka.actor.ActorSystem = system.toUntyped

  private val profile: JdbcProfile = H2Profile

  private val databaseUrl: String = persistenceConfig.getString("database.url")

  private val database: profile.backend.DatabaseDef = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = persistenceConfig.getString("database.driver"),
    keepAliveConnection = persistenceConfig.getBoolean("database.keep-alive-connection")
  )

  private val StoreInitializationResult(nodeStore, nodeStoreInit) = NodeStore(
    backend = backends.nodes,
    cachingEnabled = persistenceConfig.getBoolean("nodes.caching-enabled")
  )

  private val StoreInitializationResult(reservationStore, reservationStoreInit) = ReservationStore(
    expiration = persistenceConfig.getDuration("reservations.expiration").toSeconds.seconds,
    backend = backends.reservations
  )

  val manifests: ManifestStore = ManifestStore(backend = backends.manifests)

  val nodes: NodeStore = nodeStore

  val reservations: ReservationStore = reservationStore

  val staging: Option[StagingStore] =
    if (persistenceConfig.getBoolean("staging.enabled")) {
      val store = new StagingStore(
        crateStore = CrateStore.fromDescriptor(
          descriptor = CrateStore.Descriptor(
            config = persistenceConfig.getConfig("staging.store")
          )
        ),
        destagingDelay = persistenceConfig.getDuration("staging.destaging-delay").toSeconds.seconds
      )

      Some(store)
    } else {
      None
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

  private object backends {
    val manifests = new SlickBackend(
      tableName = "MANIFESTS",
      profile = profile,
      database = database,
      serdes = ManifestStoreSerdes
    )

    val nodes = new SlickBackend(
      tableName = "NODES",
      profile = profile,
      database = database,
      serdes = NodeStoreSerdes
    )

    val reservations = new SlickBackend(
      tableName = "RESERVATIONS",
      profile = profile,
      database = database,
      serdes = ReservationStoreSerdes
    )
  }
}
