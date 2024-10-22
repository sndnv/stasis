package stasis.server.persistence.datasets

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.packaging.Crate
import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device

class DefaultDatasetEntryStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends DatasetEntryStore {
  import profile.api._

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val dataColumnType: JdbcType[Set[Crate.Id]] =
    MappedColumnType.base[Set[Crate.Id], String](
      _.map(_.toString).mkString(","),
      _.split(",").map(_.trim).filter(_.nonEmpty).toSet.map(java.util.UUID.fromString)
    )

  private class SlickStore(tag: Tag) extends Table[DatasetEntry](tag, name) {
    def id: Rep[DatasetEntry.Id] = column[DatasetEntry.Id]("ID", O.PrimaryKey)
    def definition: Rep[DatasetDefinition.Id] = column[DatasetDefinition.Id]("DEFINITION")
    def device: Rep[Device.Id] = column[Device.Id]("DEVICE")
    def data: Rep[Set[Crate.Id]] = column[Set[Crate.Id]]("DATA")
    def metadata: Rep[Crate.Id] = column[Crate.Id]("METADATA")
    def created: Rep[Instant] = column[Instant]("CREATED")

    def * : ProvenShape[DatasetEntry] =
      (id, definition, device, data, metadata, created) <> ((DatasetEntry.apply _).tupled, DatasetEntry.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override protected[datasets] def create(entry: DatasetEntry): Future[Done] = metrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(entry))
      .map(_ => Done)
  }

  override protected[datasets] def delete(entry: DatasetEntry.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.id === entry).delete).map(_ == 1)
  }

  override protected[datasets] def get(entry: DatasetEntry.Id): Future[Option[DatasetEntry]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.id === entry).map(_.value).result.headOption)
  }

  override protected[datasets] def list(definition: DatasetDefinition.Id): Future[Seq[DatasetEntry]] =
    metrics.recordList(store = name) {
      database.run(store.filter(_.definition === definition).result)
    }

  override protected[datasets] def latest(
    definition: DatasetDefinition.Id,
    devices: Seq[Device.Id],
    until: Option[Instant]
  ): Future[Option[DatasetEntry]] = metrics.recordGet(store = name) {
    val result = store
      .filter { entry =>
        (
          entry.definition === definition
          && ((devices.isEmpty: Rep[Boolean]) || entry.device.inSet(devices))
          && until.map(entry.created < _).getOrElse(true: Rep[Boolean])
        )
      }
      .sortBy(_.created.desc)
      .take(1)
      .result
      .headOption

    database.run(result)
  }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[DatasetEntry, SlickStore](withVersion = 1, current = store) { e =>
        DatasetEntry(
          id = (e \ "id").as[DatasetEntry.Id],
          definition = (e \ "definition").as[DatasetDefinition.Id],
          device = (e \ "device").as[Device.Id],
          data = (e \ "data").as[Set[Crate.Id]],
          metadata = (e \ "metadata").as[Crate.Id],
          created = (e \ "created").as[Instant]
        )
      }
  )
}
