package stasis.identity.persistence.clients

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.ByteString
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.identity.model.Seconds
import stasis.identity.model.clients.Client
import stasis.identity.model.secrets.Secret
import stasis.identity.persistence.internal
import io.github.sndnv.layers.persistence.Metrics
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.TelemetryContext

class DefaultClientStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends ClientStore {
  import profile.api._
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val secondsColumnType: JdbcType[Seconds] =
    MappedColumnType.base[Seconds, Long](_.value, Seconds.apply)

  private implicit val secretColumnType: JdbcType[Secret] =
    MappedColumnType.base[Secret, Array[Byte]](_.value.toArray, s => Secret(ByteString(s)))

  private class SlickStore(tag: Tag) extends Table[Client](tag, name) {
    def id: Rep[Client.Id] = column[Client.Id]("ID", O.PrimaryKey)
    def redirectUri: Rep[String] = column[String]("REDIRECT_URI")
    def tokenExpiration: Rep[Seconds] = column[Seconds]("TOKEN_EXPIRATION")
    def secret: Rep[Secret] = column[Secret]("SECRET")
    def salt: Rep[String] = column[String]("SALT")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def subject: Rep[Option[String]] = column[Option[String]]("SUBJECT")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[Client] =
      (
        id,
        redirectUri,
        tokenExpiration,
        secret,
        salt,
        active,
        subject,
        created,
        updated
      ) <> ((Client.apply _).tupled, Client.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(client: Client): Future[Done] = metrics.recordPut(store = name) {
    database.run(store.insertOrUpdate(client)).map(_ => Done)
  }

  override def delete(client: Client.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database
      .run(store.filter(_.id === client).delete.map(_ == 1))
  }

  override def get(client: Client.Id): Future[Option[Client]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.id === client).map(_.value).result.headOption)
  }

  override def all: Future[Seq[Client]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override val migrations: Seq[Migration] = Seq(
    internal
      .LegacyKeyValueStore(name, profile, database)
      .asMigration[Client, SlickStore](withVersion = 1, current = store) { e =>
        import java.util.Base64

        Client(
          id = (e \ "id").as[Client.Id],
          redirectUri = (e \ "redirectUri").as[String],
          tokenExpiration = Seconds((e \ "tokenExpiration").as[Long]),
          secret = Secret(ByteString(Base64.getUrlDecoder.decode((e \ "secret").as[String]))),
          salt = (e \ "salt").as[String],
          active = (e \ "active").as[Boolean],
          subject = (e \ "subject").asOpt[String],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
