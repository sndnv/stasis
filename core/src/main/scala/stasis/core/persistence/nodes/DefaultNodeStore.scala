package stasis.core.persistence.nodes

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import play.api.libs.json.Json
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.networking.EndpointAddress
import stasis.core.networking.grpc.GrpcEndpointAddress
import stasis.core.networking.http.HttpEndpointAddress
import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.core.persistence.crates.CrateStore
import stasis.core.routing.Node
import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.Metrics
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.TelemetryContext

class DefaultNodeStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database,
  cache: KeyValueStore[Node.Id, Node]
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends NodeStore {
  import profile.api._
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  import stasis.core.api.Formats.addressFormat
  import stasis.core.api.Formats.crateStoreDescriptorReads
  import stasis.core.api.Formats.crateStoreDescriptorWrites

  private implicit val addressColumnType: JdbcType[EndpointAddress] =
    MappedColumnType.base[EndpointAddress, String](
      address => Json.toJson(address).toString(),
      address => Json.parse(address).as[EndpointAddress]
    )

  private implicit val descriptorColumnType: JdbcType[CrateStore.Descriptor] =
    MappedColumnType.base[CrateStore.Descriptor, String](
      descriptor => Json.toJson(descriptor).toString(),
      descriptor => Json.parse(descriptor).as[CrateStore.Descriptor]
    )

  private class SlickStore(tag: Tag) extends Table[Node](tag, name) {
    def id: Rep[Node.Id] = column[Node.Id]("ID", O.PrimaryKey)
    def nodeType: Rep[String] = column[String]("NODE_TYPE")
    def storageAllowed: Rep[Boolean] = column[Boolean]("STORAGE_ALLOWED")
    def address: Rep[Option[EndpointAddress]] = column[Option[EndpointAddress]]("ADDRESS")
    def descriptor: Rep[Option[CrateStore.Descriptor]] = column[Option[CrateStore.Descriptor]]("DESCRIPTOR")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[Node] =
      (id, nodeType, storageAllowed, address, descriptor, created, updated) <> ((Node.apply _).tupled, Node.unapply)
  }

  private val store = TableQuery[SlickStore]

  def prepare(): Future[Done] =
    database
      .run(store.result)
      .flatMap(entries => cache.load(entries.map { e => e.id -> e }.toMap))

  override def init(): Future[Done] =
    for {
      _ <- database.run(store.schema.create)
      _ <- cache.init()
    } yield {
      Done
    }

  override def drop(): Future[Done] =
    for {
      _ <- database.run(store.schema.drop)
      _ <- cache.drop()
    } yield {
      Done
    }

  override def put(node: Node): Future[Done] =
    metrics.recordPut(store = name) {
      for {
        _ <- database.run(store.insertOrUpdate(node))
        _ <- cache.put(node.id, node)
      } yield {
        Done
      }
    }

  override def delete(node: Node.Id): Future[Boolean] =
    metrics.recordDelete(store = name) {
      for {
        result <- database.run(store.filter(_.id === node).delete)
        _ <- cache.delete(node)
      } yield {
        result == 1
      }
    }

  override def get(node: Node.Id): Future[Option[Node]] =
    metrics.recordGet(store = name) {
      cache.get(node)
    }

  override def contains(node: Node.Id): Future[Boolean] =
    metrics.recordContains(store = name) {
      cache.get(node).map(_.isDefined)(ExecutionContext.parasitic)
    }

  override def nodes: Future[Map[Node.Id, Node]] =
    metrics.recordList(store = name) {
      cache.entries
    }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[Node, SlickStore](withVersion = 1, current = store) { node =>
        import stasis.core.api.Formats.grpcEndpointAddressFormat
        import stasis.core.api.Formats.httpEndpointAddressFormat

        (node \ "node_type").as[String] match {
          case "local" =>
            val id = (node \ "id").as[Node.Id]
            Node.Local(
              id = id,
              storeDescriptor = (node \ "store_descriptor").as[CrateStore.Descriptor],
              created = Instant.now(),
              updated = Instant.now()
            )

          case "remote-http" =>
            Node.Remote.Http(
              id = (node \ "id").as[Node.Id],
              address = (node \ "address").as[HttpEndpointAddress],
              storageAllowed = (node \ "storage_allowed").as[Boolean],
              created = Instant.now(),
              updated = Instant.now()
            )

          case "remote-grpc" =>
            Node.Remote.Grpc(
              id = (node \ "id").as[Node.Id],
              address = (node \ "address").as[GrpcEndpointAddress],
              storageAllowed = (node \ "storage_allowed").as[Boolean],
              created = Instant.now(),
              updated = Instant.now()
            )
        }
      }
  )
}
