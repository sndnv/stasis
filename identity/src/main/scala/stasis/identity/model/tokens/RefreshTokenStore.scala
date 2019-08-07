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
    backend: KeyValueBackend[RefreshToken, StoredRefreshToken]
  )(implicit system: ActorSystem[SpawnProtocol]): RefreshTokenStore =
    new RefreshTokenStore {
      private implicit val ec: ExecutionContext = system.executionContext

      override def put(
        client: Client.Id,
        token: RefreshToken,
        owner: ResourceOwner,
        scope: Option[String]
      ): Future[Done] =
        backend
          .put(token, StoredRefreshToken(token, client, owner, scope, Instant.now().plusSeconds(expiration.toSeconds)))
          .map { result =>
            val _ = expire(token)
            result
          }

      override def delete(token: RefreshToken): Future[Boolean] =
        backend.delete(token)

      override def get(token: RefreshToken): Future[Option[StoredRefreshToken]] =
        backend.get(token)

      override def tokens: Future[Map[RefreshToken, StoredRefreshToken]] =
        backend.entries

      private val _ = backend.entries.flatMap { entries =>
        val now = Instant.now()

        Future.sequence(
          entries.map {
            case (token, storedToken) if storedToken.expiration.isBefore(now) => backend.delete(token)
            case (token, _)                                                   => expire(token)
          }
        )
      }

      private def expire(token: RefreshToken): Future[Boolean] =
        akka.pattern.after(expiration, system.scheduler)(backend.delete(token))
    }
}
