package stasis.identity.model.tokens

import java.time.Instant

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
trait RefreshTokenStore { store =>
  def put(client: Client.Id, token: RefreshToken, owner: ResourceOwner, scope: Option[String]): Future[Done]
  def delete(token: RefreshToken): Future[Boolean]
  def get(token: RefreshToken): Future[Option[StoredRefreshToken]]
  def tokens: Future[Map[RefreshToken, StoredRefreshToken]]
}

object RefreshTokenStore {
  def apply(
    expiration: FiniteDuration,
    backend: KeyValueBackend[RefreshToken, StoredRefreshToken],
    directory: KeyValueBackend[(Client.Id, ResourceOwner.Id), RefreshToken]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): RefreshTokenStore =
    new RefreshTokenStore {
      private val untypedSystem = system.classicSystem
      private implicit val ec: ExecutionContext = system.executionContext

      override def put(
        client: Client.Id,
        token: RefreshToken,
        owner: ResourceOwner,
        scope: Option[String]
      ): Future[Done] =
        for {
          _ <- dropExisting(client, owner.username)
          _ <- backend.put(
            key = token,
            value = StoredRefreshToken(
              token = token,
              client = client,
              owner = owner,
              scope = scope,
              expiration = Instant.now().plusSeconds(expiration.toSeconds)
            )
          )
          _ <- directory.put(client -> owner.username, token)
        } yield {
          val _ = expire(token)
          Done
        }

      override def delete(token: RefreshToken): Future[Boolean] =
        backend
          .get(token)
          .flatMap {
            case Some(existingToken) => directory.delete(existingToken.client -> existingToken.owner.username)
            case None                => Future.successful(false)
          }
          .flatMap(_ => backend.delete(token))

      override def get(token: RefreshToken): Future[Option[StoredRefreshToken]] =
        backend.get(token)

      override def tokens: Future[Map[RefreshToken, StoredRefreshToken]] =
        backend.entries

      private val _ = backend.entries.flatMap { entries =>
        val now = Instant.now()

        Future.sequence(
          entries.map {
            case (token, storedToken) if storedToken.expiration.isBefore(now) =>
              backend.delete(token)

            case (token, storedToken) =>
              directory
                .put(storedToken.client -> storedToken.owner.username, storedToken.token)
                .flatMap(_ => expire(token))
          }
        )
      }

      private def dropExisting(client: Client.Id, owner: ResourceOwner.Id): Future[Done] =
        directory
          .get(client -> owner)
          .flatMap {
            case Some(existingToken) =>
              for {
                _ <- directory.delete(client -> owner)
                _ <- backend.delete(existingToken)
              } yield {
                Done
              }

            case None =>
              Future.successful(Done)
          }

      private def expire(token: RefreshToken): Future[Boolean] =
        akka.pattern.after(expiration, untypedSystem.scheduler)(backend.delete(token))
    }
}
