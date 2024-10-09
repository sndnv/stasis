package stasis.identity.persistence.tokens
import java.time.Instant

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import slick.jdbc.JdbcProfile
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.persistence.internal
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.Metrics
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.TelemetryContext

class DefaultRefreshTokenStore(
  override val name: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database,
  expiration: FiniteDuration,
  directory: KeyValueStore[(Client.Id, ResourceOwner.Id), RefreshToken]
)(implicit val system: ActorSystem[Nothing], telemetry: TelemetryContext)
    extends RefreshTokenStore {
  import profile.api._
  import system.executionContext

  private val metrics = telemetry.metrics[Metrics.Store]

  private implicit val tokenColumnType: JdbcType[RefreshToken] =
    MappedColumnType.base[RefreshToken, String](_.value, RefreshToken.apply)

  private class SlickAccountStore(tag: Tag) extends Table[StoredRefreshToken](tag, name) {
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
  }

  private val store = TableQuery[SlickAccountStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).flatMap(_ => directory.init())

  override def drop(): Future[Done] =
    database.run(store.schema.drop).flatMap(_ => directory.drop())

  override def put(
    client: Client.Id,
    token: RefreshToken,
    owner: ResourceOwner,
    scope: Option[String]
  ): Future[Done] =
    for {
      _ <- dropExisting(client, owner.username)
      now = Instant.now()
      value = StoredRefreshToken(
        token = token,
        client = client,
        owner = owner.username,
        scope = scope,
        expiration = now.plusSeconds(expiration.toSeconds),
        created = now
      )
      _ <- database.run(store.insertOrUpdate(value))
      _ = metrics.recordPut(store = name)
      _ <- directory.put(client -> owner.username, token)
    } yield {
      val _ = expire(token)
      Done
    }

  override def delete(token: RefreshToken): Future[Boolean] =
    get(token)
      .flatMap {
        case Some(existingToken) => directory.delete(existingToken.client -> existingToken.owner)
        case None                => Future.successful(false)
      }
      .flatMap(_ => database.run(store.filter(_.token === token).delete).map(_ == 1))
      .map { result =>
        metrics.recordDelete(store = name)
        result
      }

  override def get(token: RefreshToken): Future[Option[StoredRefreshToken]] =
    database
      .run(store.filter(_.token === token).map(_.value).result.headOption)
      .map { result =>
        result.foreach(_ => metrics.recordGet(store = name))
        result
      }

  override def all: Future[Seq[StoredRefreshToken]] =
    database
      .run(store.result)
      .map { result =>
        if (result.nonEmpty) {
          metrics.recordGet(store = name, entries = result.size)
        }
        result
      }

  private def dropExisting(client: Client.Id, owner: ResourceOwner.Id): Future[Done] =
    directory
      .get(client -> owner)
      .flatMap {
        case Some(existingToken) =>
          for {
            _ <- directory.delete(client -> owner)
            _ <- delete(existingToken)
          } yield {
            Done
          }

        case None =>
          Future.successful(Done)
      }

  private def expire(token: RefreshToken): Future[Boolean] =
    org.apache.pekko.pattern.after(expiration)(delete(token))

  override val migrations: Seq[Migration] = Seq(
    internal
      .LegacyKeyValueStore(name, profile, database)
      .asMigration[StoredRefreshToken, SlickAccountStore](withVersion = 1, current = store) { e =>
        StoredRefreshToken(
          token = RefreshToken((e \ "token").as[String]),
          client = (e \ "client").as[Client.Id],
          owner = (e \ "owner").as[ResourceOwner.Id],
          scope = (e \ "scope").asOpt[String],
          expiration = (e \ "expiration").as[Instant],
          created = Instant.now()
        )
      }
  )

  locally {
    val _ = all.flatMap { entries =>
      val now = Instant.now()

      Future.sequence(
        entries.map {
          case storedToken if storedToken.expiration.isBefore(now) =>
            delete(storedToken.token)

          case storedToken =>
            directory
              .put(storedToken.client -> storedToken.owner, storedToken.token)
              .flatMap(_ => expire(storedToken.token))
        }
      )
    }
  }
}
