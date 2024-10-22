package stasis.identity.persistence.owners

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.ByteString
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.internal
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class DefaultResourceOwnerStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ResourceOwnerStore {
  import profile.api._
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val scopesColumnType: JdbcType[Seq[String]] =
    MappedColumnType.base[Seq[String], String](_.mkString(","), _.split(",").toSeq)

  private implicit val secretColumnType: JdbcType[Secret] =
    MappedColumnType.base[Secret, Array[Byte]](_.value.toArray, s => Secret(ByteString(s)))

  private class SlickStore(tag: Tag) extends Table[ResourceOwner](tag, name) {
    def username: Rep[ResourceOwner.Id] = column[ResourceOwner.Id]("USERNAME", O.PrimaryKey)
    def password: Rep[Secret] = column[Secret]("PASSWORD")
    def salt: Rep[String] = column[String]("SALT")
    def allowedScopes: Rep[Seq[String]] = column[Seq[String]]("ALLOWED_SCOPES")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def subject: Rep[Option[String]] = column[Option[String]]("SUBJECT")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[ResourceOwner] =
      (
        username,
        password,
        salt,
        allowedScopes,
        active,
        subject,
        created,
        updated,
      ) <> ((ResourceOwner.apply _).tupled, ResourceOwner.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(owner: ResourceOwner): Future[Done] = metrics.recordPut(store = name) {
    database.run(store.insertOrUpdate(owner)).map(_ => Done)
  }

  override def delete(owner: ResourceOwner.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.username === owner).delete.map(_ == 1))
  }

  override def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.username === owner).map(_.value).result.headOption)
  }

  override def all: Future[Seq[ResourceOwner]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override def contains(owner: ResourceOwner.Id): Future[Boolean] = metrics.recordContains(store = name) {
    database.run(store.filter(_.username === owner).exists.result)
  }

  override val migrations: Seq[Migration] = Seq(
    internal
      .LegacyKeyValueStore(name, profile, database)
      .asMigration[ResourceOwner, SlickStore](withVersion = 1, current = store) { e =>
        import java.util.Base64

        ResourceOwner(
          username = (e \ "username").as[String],
          password = Secret(ByteString(Base64.getUrlDecoder.decode((e \ "password").as[String]))),
          salt = (e \ "salt").as[String],
          allowedScopes = (e \ "allowedScopes").as[Seq[String]],
          active = (e \ "active").as[Boolean],
          subject = (e \ "subject").asOpt[String],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
