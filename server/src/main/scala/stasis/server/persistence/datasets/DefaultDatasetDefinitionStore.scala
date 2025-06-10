package stasis.server.persistence.datasets

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
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device

class DefaultDatasetDefinitionStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends DatasetDefinitionStore {
  import profile.api._

  import stasis.shared.api.Formats._

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val retentionColumnType: JdbcType[DatasetDefinition.Retention] =
    MappedColumnType.base[DatasetDefinition.Retention, String](
      retention => Json.toJson(retention).toString(),
      retention => Json.parse(retention).as[DatasetDefinition.Retention]
    )

  private class SlickStore(tag: Tag) extends Table[DatasetDefinition](tag, name) {
    def id: Rep[DatasetDefinition.Id] = column[DatasetDefinition.Id]("ID", O.PrimaryKey)
    def info: Rep[String] = column[String]("INFO")
    def device: Rep[Device.Id] = column[Device.Id]("DEVICE")
    def redundantCopies: Rep[Int] = column[Int]("REDUNDANT_COPIES")
    def existingVersions: Rep[DatasetDefinition.Retention] = column[DatasetDefinition.Retention]("EXISTING_VERSIONS")
    def removedVersions: Rep[DatasetDefinition.Retention] = column[DatasetDefinition.Retention]("REMOVED_VERSIONS")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[DatasetDefinition] =
      (
        id,
        info,
        device,
        redundantCopies,
        existingVersions,
        removedVersions,
        created,
        updated
      ) <> ((DatasetDefinition.apply _).tupled, DatasetDefinition.unapply)
  }

  private val store = TableQuery[SlickStore]

  override protected[datasets] def put(definition: DatasetDefinition): Future[Done] = metrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(definition))
      .map(_ => Done)
  }

  override protected[datasets] def delete(definition: DatasetDefinition.Id): Future[Boolean] =
    metrics.recordDelete(store = name) {
      database.run(store.filter(_.id === definition).delete).map(_ == 1)
    }

  override protected[datasets] def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]] =
    metrics.recordGet(store = name) {
      database.run(store.filter(_.id === definition).map(_.value).result.headOption)
    }

  override protected[datasets] def list(): Future[Seq[DatasetDefinition]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[DatasetDefinition, SlickStore](withVersion = 1, current = store) { e =>
        DatasetDefinition(
          id = (e \ "id").as[DatasetDefinition.Id],
          info = (e \ "info").as[String],
          device = (e \ "device").as[Device.Id],
          redundantCopies = (e \ "redundant_copies").as[Int],
          existingVersions = (e \ "existing_versions").as[DatasetDefinition.Retention],
          removedVersions = (e \ "removed_versions").as[DatasetDefinition.Retention],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
