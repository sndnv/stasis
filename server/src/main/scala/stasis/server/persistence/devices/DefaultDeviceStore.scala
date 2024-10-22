package stasis.server.persistence.devices

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import play.api.libs.json.Json
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.core.routing.Node
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

class DefaultDeviceStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends DeviceStore {
  import profile.api._

  import stasis.shared.api.Formats._

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val deviceLimitsColumnType: JdbcType[Device.Limits] =
    MappedColumnType.base[Device.Limits, String](
      limits => Json.toJson(limits).toString(),
      limits => Json.parse(limits).as[Device.Limits]
    )

  private class SlickStore(tag: Tag) extends Table[Device](tag, name) {
    def id: Rep[Device.Id] = column[Device.Id]("ID", O.PrimaryKey)
    def name: Rep[String] = column[String]("NAME")
    def node: Rep[Node.Id] = column[Node.Id]("NODE")
    def owner: Rep[User.Id] = column[User.Id]("OWNER")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def limits: Rep[Option[Device.Limits]] = column[Option[Device.Limits]]("LIMITS")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[Device] =
      (id, name, node, owner, active, limits, created, updated) <> ((Device.apply _).tupled, Device.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override protected[devices] def put(device: Device): Future[Done] = metrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(device))
      .map(_ => Done)
  }

  override protected[devices] def delete(device: Device.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.id === device).delete).map(_ == 1)
  }

  override protected[devices] def get(device: Device.Id): Future[Option[Device]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.id === device).map(_.value).result.headOption)
  }

  override protected[devices] def list(): Future[Seq[Device]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[Device, SlickStore](withVersion = 1, current = store) { e =>
        Device(
          id = (e \ "id").as[User.Id],
          name = (e \ "name").as[String],
          node = (e \ "node").as[Node.Id],
          owner = (e \ "owner").as[User.Id],
          active = (e \ "active").as[Boolean],
          limits = (e \ "limits").asOpt[Device.Limits],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
