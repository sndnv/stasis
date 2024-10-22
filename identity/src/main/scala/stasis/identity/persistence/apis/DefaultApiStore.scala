package stasis.identity.persistence.apis

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import stasis.identity.model.apis.Api
import stasis.identity.persistence.internal
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class DefaultApiStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ApiStore {
  import profile.api._
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private class SlickStore(tag: Tag) extends Table[Api](tag, name) {
    def id: Rep[Api.Id] = column[Api.Id]("ID", O.PrimaryKey)
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[Api] =
      (id, created, updated) <> ((Api.apply _).tupled, Api.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(api: Api): Future[Done] = metrics.recordPut(store = name) {
    database.run(store.insertOrUpdate(api)).map(_ => Done)
  }

  override def delete(api: Api.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.id === api).delete).map(_ == 1)
  }

  override def get(api: Api.Id): Future[Option[Api]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.id === api).map(_.value).result.headOption)
  }

  override def all: Future[Seq[Api]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override def contains(api: Api.Id): Future[Boolean] = metrics.recordContains(store = name) {
    database.run(store.filter(_.id === api).exists.result)
  }

  override val migrations: Seq[Migration] = Seq(
    internal
      .LegacyKeyValueStore(name, profile, database)
      .asMigration[Api, SlickStore](withVersion = 1, current = store) { e =>
        Api(
          id = (e \ "id").as[String],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
