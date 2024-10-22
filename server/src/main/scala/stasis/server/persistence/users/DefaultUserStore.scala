package stasis.server.persistence.users

import java.time.Instant
import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

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
import stasis.shared.model.users.User
import stasis.shared.security.Permission

class DefaultUserStore(
  override val name: String,
  userSaltSize: Int,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends UserStore {
  import profile.api._

  import stasis.shared.api.Formats._

  override protected implicit val ec: ExecutionContext = system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val userLimitsColumnType: JdbcType[User.Limits] =
    MappedColumnType.base[User.Limits, String](
      limits => Json.toJson(limits).toString(),
      limits => Json.parse(limits).as[User.Limits]
    )

  private implicit val permissionsColumnType: JdbcType[Set[Permission]] =
    MappedColumnType.base[Set[Permission], String](
      _.map(permissionToString).toSeq.sorted.mkString(","),
      _.split(",").map(_.trim).filter(_.nonEmpty).toSet.map(stringToPermission)
    )

  private class SlickStore(tag: Tag) extends Table[User](tag, name) {
    def id: Rep[User.Id] = column[User.Id]("ID", O.PrimaryKey)
    def salt: Rep[String] = column[String]("SALT")
    def active: Rep[Boolean] = column[Boolean]("ACTIVE")
    def limits: Rep[Option[User.Limits]] = column[Option[User.Limits]]("LIMITS")
    def permissions: Rep[Set[Permission]] = column[Set[Permission]]("PERMISSIONS")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")

    def * : ProvenShape[User] =
      (id, salt, active, limits, permissions, created, updated) <> ((User.apply _).tupled, User.unapply)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override protected[users] def put(user: User): Future[Done] = metrics.recordPut(store = name) {
    database
      .run(store.insertOrUpdate(user))
      .map(_ => Done)
  }

  override protected[users] def delete(user: User.Id): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.id === user).delete).map(_ == 1)
  }

  override protected[users] def get(user: User.Id): Future[Option[User]] = metrics.recordGet(store = name) {
    database.run(store.filter(_.id === user).map(_.value).result.headOption)
  }

  override protected[users] def list(): Future[Seq[User]] = metrics.recordList(store = name) {
    database.run(store.result)
  }

  override protected[users] def generateSalt(): String = {
    val rnd: Random = ThreadLocalRandom.current()
    rnd.alphanumeric.take(userSaltSize).mkString
  }

  override val migrations: Seq[Migration] = Seq(
    LegacyKeyValueStore(name, profile, database)
      .asMigration[User, SlickStore](withVersion = 1, current = store) { e =>
        User(
          id = (e \ "id").as[User.Id],
          salt = (e \ "salt").as[String],
          active = (e \ "active").as[Boolean],
          limits = (e \ "limits").asOpt[User.Limits],
          permissions = (e \ "permissions").as[Set[Permission]],
          created = Instant.now(),
          updated = Instant.now()
        )
      }
  )
}
