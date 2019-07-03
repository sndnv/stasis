package stasis.identity.model.tokens

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait RefreshTokenStore { store =>
  def put(client: Client.Id, token: RefreshToken, owner: ResourceOwner, scope: Option[String]): Future[Done]
  def delete(client: Client.Id): Future[Boolean]
  def get(client: Client.Id): Future[Option[StoredRefreshToken]]
  def tokens: Future[Map[Client.Id, StoredRefreshToken]]
}

object RefreshTokenStore {
  def apply(
    expiration: FiniteDuration,
    backend: KeyValueBackend[Client.Id, StoredRefreshToken]
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
          .put(client, StoredRefreshToken(token, owner, scope))
          .map { result =>
            val _ = akka.pattern.after(expiration, system.scheduler)(backend.delete(client))
            result
          }

      override def delete(client: Client.Id): Future[Boolean] =
        backend.delete(client)

      override def get(client: Client.Id): Future[Option[StoredRefreshToken]] =
        backend.get(client)

      override def tokens: Future[Map[Client.Id, StoredRefreshToken]] =
        backend.entries
    }
}
