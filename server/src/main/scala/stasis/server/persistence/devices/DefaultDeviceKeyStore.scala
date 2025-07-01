package stasis.server.persistence.devices

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.ByteString
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import io.github.sndnv.layers.persistence.Metrics
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.shared.model.devices.Device
import stasis.shared.model.devices.DeviceKey
import stasis.shared.model.users.User

class DefaultDeviceKeyStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends DeviceKeyStore {
  import profile.api._

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val keyValueColumnType: JdbcType[ByteString] =
    MappedColumnType.base[ByteString, Array[Byte]](_.toArray, s => ByteString(s))

  private class SlickStore(tag: Tag) extends Table[DeviceKey](tag, name) {
    def value: Rep[ByteString] = column[ByteString]("VALUE")
    def owner: Rep[User.Id] = column[User.Id]("OWNER")
    def device: Rep[Device.Id] = column[Device.Id]("DEVICE", O.PrimaryKey)
    def created: Rep[Instant] = column[Instant]("CREATED")

    def * : ProvenShape[DeviceKey] =
      (value, owner, device, created) <> ((DeviceKey.apply _).tupled, DeviceKey.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override protected[devices] def put(key: DeviceKey): Future[Done] = metrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(key))
      .map(_ => Done)
  }

  override protected[devices] def delete(forDevice: Device.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.device === forDevice).delete).map(_ == 1)
  }

  override protected[devices] def exists(forDevice: Device.Id): Future[Boolean] = metrics.recordGet(store = name) {
    database.run(store.filter(_.device === forDevice).exists.result)
  }

  override protected[devices] def get(forDevice: Device.Id): Future[Option[DeviceKey]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.device === forDevice).result.headOption)
  }

  override protected[devices] def list(): Future[Seq[DeviceKey]] = metrics.recordList(store = name) {
    database
      .run(store.result)
      .map(_.map(_.copy(value = ByteString.empty)))
  }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[DeviceKey, SlickStore](withVersion = 1, current = store) { e =>
        import io.github.sndnv.layers.api.Formats.byteStringFormat
        DeviceKey(
          value = (e \ "value").as[ByteString],
          owner = (e \ "owner").as[User.Id],
          device = (e \ "device").as[Device.Id],
          created = Instant.now()
        )
      }
  )
}
