package stasis.server.persistence.schedules

import java.time.Instant
import java.time.LocalDateTime

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.core.persistence.backends.slick.LegacyKeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.schedules.Schedule

class DefaultScheduleStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ScheduleStore {
  import profile.api._

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val durationColumnType: JdbcType[FiniteDuration] =
    MappedColumnType.base[FiniteDuration, Long](_.toSeconds, _.seconds)

  private class SlickStore(tag: Tag) extends Table[Schedule](tag, name) {
    def id: Rep[Schedule.Id] = column[Schedule.Id]("ID", O.PrimaryKey)
    def info: Rep[String] = column[String]("INFO")
    def isPublic: Rep[Boolean] = column[Boolean]("IS_PUBLIC")
    def start: Rep[LocalDateTime] = column[LocalDateTime]("START")
    def interval: Rep[FiniteDuration] = column[FiniteDuration]("INTERVAL")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[Schedule] =
      (id, info, isPublic, start, interval, created, updated) <> ((Schedule.apply _).tupled, Schedule.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override protected[schedules] def put(schedule: Schedule): Future[Done] = metrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(schedule))
      .map(_ => Done)
  }

  override protected[schedules] def delete(schedule: Schedule.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.id === schedule).delete).map(_ == 1)
  }

  override protected[schedules] def get(schedule: Schedule.Id): Future[Option[Schedule]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.id === schedule).map(_.value).result.headOption)
  }

  override protected[schedules] def list(): Future[Seq[Schedule]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[Schedule, SlickStore](withVersion = 1, current = store) { e =>
        import stasis.layers.api.Formats.finiteDurationFormat

        Schedule(
          id = (e \ "id").as[Schedule.Id],
          info = (e \ "info").as[String],
          isPublic = (e \ "is_public").as[Boolean],
          start = (e \ "start").as[LocalDateTime],
          interval = (e \ "interval").as[FiniteDuration],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
