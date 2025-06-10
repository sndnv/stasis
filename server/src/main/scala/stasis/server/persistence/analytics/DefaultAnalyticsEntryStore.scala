package stasis.server.persistence.analytics

import java.time.Instant

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import play.api.libs.json.Json
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry

class DefaultAnalyticsEntryStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends AnalyticsEntryStore {
  import profile.api._

  import stasis.layers.api.Formats.analyticsEntryEventFormat
  import stasis.layers.api.Formats.analyticsEntryFailureFormat

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val analyticsEntryEventsType: JdbcType[Seq[AnalyticsEntry.Event]] =
    MappedColumnType.base[Seq[AnalyticsEntry.Event], String](
      events => Json.toJson(events).toString(),
      events => Json.parse(events).as[Seq[AnalyticsEntry.Event]]
    )

  private implicit val analyticsEntryFailuresType: JdbcType[Seq[AnalyticsEntry.Failure]] =
    MappedColumnType.base[Seq[AnalyticsEntry.Failure], String](
      failures => Json.toJson(failures).toString(),
      failures => Json.parse(failures).as[Seq[AnalyticsEntry.Failure]]
    )

  private class SlickStore(tag: Tag) extends Table[StoredAnalyticsEntry](tag, name) {
    def id: Rep[StoredAnalyticsEntry.Id] = column[StoredAnalyticsEntry.Id]("ENTRY_ID", O.PrimaryKey)
    def runtimeId: Rep[String] = column[String]("RUNTIME_ID")
    def runtimeApp: Rep[String] = column[String]("RUNTIME_APP")
    def runtimeJre: Rep[String] = column[String]("RUNTIME_JRE")
    def runtimeOs: Rep[String] = column[String]("RUNTIME_OS")
    def events: Rep[Seq[AnalyticsEntry.Event]] = column[Seq[AnalyticsEntry.Event]]("EVENTS")
    def failures: Rep[Seq[AnalyticsEntry.Failure]] = column[Seq[AnalyticsEntry.Failure]]("FAILURES")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")
    def received: Rep[Instant] = column[Instant]("RECEIVED")

    def * : ProvenShape[StoredAnalyticsEntry] =
      (
        id,
        runtimeId,
        runtimeApp,
        runtimeJre,
        runtimeOs,
        events,
        failures,
        created,
        updated,
        received
      ) <> ((StoredAnalyticsEntry.fromFlattened _).tupled, StoredAnalyticsEntry.flattened)
  }

  private val store = TableQuery[SlickStore]

  override protected[analytics] def put(entry: StoredAnalyticsEntry): Future[Done] =
    metrics.recordPut(store = name) {
      database
        .run(store.insertOrUpdate(entry))
        .map(_ => Done)
    }

  override protected[analytics] def delete(entry: StoredAnalyticsEntry.Id): Future[Boolean] =
    metrics.recordDelete(store = name) {
      database.run(store.filter(_.id === entry).delete).map(_ == 1)
    }

  override protected[analytics] def get(entry: StoredAnalyticsEntry.Id): Future[Option[StoredAnalyticsEntry]] =
    metrics.recordGet(store = name) {
      database.run(store.filter(_.id === entry).map(_.value).result.headOption)
    }

  override protected[analytics] def list(): Future[Seq[StoredAnalyticsEntry]] =
    metrics.recordList(store = name) {
      database.run(store.result)
    }

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override val migrations: Seq[Migration] = Seq(
    Migration(
      version = 1,
      needed = Migration.Action {
        database
          .run(slick.jdbc.meta.MTable.getTables(cat = None, schemaPattern = None, namePattern = Some(name), types = None))
          .map(_.headOption.isEmpty)
      },
      action = Migration.Action {
        init()
      }
    )
  )
}
