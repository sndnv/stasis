package stasis.identity.persistence.tokens

import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.Index
import slick.lifted.ProvenShape

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.persistence.internal
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class DefaultRefreshTokenStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database,
  expiration: FiniteDuration
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends RefreshTokenStore {
  import profile.api._
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val tokenColumnType: JdbcType[RefreshToken] =
    MappedColumnType.base[RefreshToken, String](_.value, RefreshToken.apply)

  private[tokens] val clientOwnerIndexName: String = s"${name}_UNIQUE_CLIENT_OWNER"

  private class SlickStore(tag: Tag) extends Table[StoredRefreshToken](tag, name) {
    def token: Rep[RefreshToken] = column[RefreshToken]("TOKEN", O.PrimaryKey)
    def client: Rep[Client.Id] = column[Client.Id]("CLIENT")
    def owner: Rep[ResourceOwner.Id] = column[ResourceOwner.Id]("OWNER")
    def scope: Rep[Option[String]] = column[Option[String]]("SCOPE")
    def expiration: Rep[Instant] = column[Instant]("EXPIRATION")
    def created: Rep[Instant] = column[Instant]("CREATED")

    def * : ProvenShape[StoredRefreshToken] =
      (
        token,
        client,
        owner,
        scope,
        expiration,
        created
      ) <> ((StoredRefreshToken.apply _).tupled, StoredRefreshToken.unapply)

    def index_unique_client_owner: Index =
      index(clientOwnerIndexName, (client, owner), unique = true)
  }

  private val store = TableQuery[SlickStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def put(
    client: Client.Id,
    token: RefreshToken,
    owner: ResourceOwner,
    scope: Option[String]
  ): Future[Done] = metrics.recordPut(store = name) {
    val now = Instant.now()

    val stored = StoredRefreshToken(
      token = token,
      client = client,
      owner = owner.username,
      scope = scope,
      expiration = now.plusSeconds(expiration.toSeconds),
      created = now
    )

    for {
      _ <- delete(client, owner)
      _ <- database.run(store.insertOrUpdate(stored))
    } yield {
      val _ = expire(token)
      Done
    }
  }

  override def delete(token: RefreshToken): Future[Boolean] = metrics.recordDelete(store = name) {
    database.run(store.filter(_.token === token).delete).map(_ == 1)
  }

  override def get(token: RefreshToken): Future[Option[StoredRefreshToken]] = metrics.recordGet(store = name) {
    database.run(store.filter(e => e.token === token && e.expiration > Instant.now()).map(_.value).result.headOption)
  }

  override def all: Future[Seq[StoredRefreshToken]] = metrics.recordList(store = name) {
    val now = Instant.now()
    database.run(store.filter(_.expiration > now).result)
  }

  private def delete(client: Client.Id, owner: ResourceOwner): Future[Done] =
    database.run(store.filter(e => e.client === client && e.owner === owner.username).delete).map(_ => Done)

  private def expire(token: RefreshToken): Future[Boolean] =
    org.apache.pekko.pattern.after(expiration)(delete(token))

  override val migrations: Seq[Migration] = Seq(
    internal
      .LegacyKeyValueStore(name, profile, database)
      .asMigration[StoredRefreshToken, SlickStore](withVersion = 1, current = store) { e =>
        StoredRefreshToken(
          token = RefreshToken((e \ "token").as[String]),
          client = (e \ "client").as[Client.Id],
          owner = (e \ "owner" \ "username").as[ResourceOwner.Id],
          scope = (e \ "scope").asOpt[String],
          expiration = (e \ "expiration").as[Instant],
          created = Instant.now()
        )
      },
    Migration(
      version = 2,
      needed = Migration.Action {
        for {
          table <- database.run(
            slick.jdbc.meta.MTable
              .getTables(cat = None, schemaPattern = None, namePattern = Some(name), types = None)
              .map(_.headOption)
          )
          indices <- table.map(t => database.run(t.getIndexInfo(unique = true))).getOrElse(Future.successful(Seq.empty))
        } yield {
          table.nonEmpty && !indices.flatMap(_.indexName).contains(clientOwnerIndexName)
        }
      },
      action = Migration.Action {
        database.run(sqlu"""CREATE UNIQUE INDEX "#$clientOwnerIndexName" ON "#$name" ("CLIENT","OWNER")""")
      }
    )
  )

  locally {
    val _ = database.run(store.result).flatMap { entries =>
      val now = Instant.now()

      Future.sequence(
        entries.collect {
          case storedToken if storedToken.expiration.isBefore(now) =>
            delete(storedToken.token)
        }
      )
    }
  }
}
