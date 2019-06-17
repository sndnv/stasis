package stasis.identity.model.codes

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.persistence.backends.KeyValueBackend
import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner

trait AuthorizationCodeStore {
  def put(client: Client.Id, code: AuthorizationCode, owner: ResourceOwner, scope: Option[String]): Future[Done]
  def delete(client: Client.Id): Future[Boolean]
  def get(client: Client.Id): Future[Option[StoredAuthorizationCode]]
  def codes: Future[Map[Client.Id, StoredAuthorizationCode]]
}

object AuthorizationCodeStore {
  def apply(
    expiration: FiniteDuration,
    backend: KeyValueBackend[Client.Id, StoredAuthorizationCode]
  )(implicit system: ActorSystem[SpawnProtocol]): AuthorizationCodeStore =
    new AuthorizationCodeStore {
      private implicit val ec: ExecutionContext = system.executionContext

      override def put(
        client: Client.Id,
        code: AuthorizationCode,
        owner: ResourceOwner,
        scope: Option[String]
      ): Future[Done] =
        backend
          .put(client, StoredAuthorizationCode(code, owner, scope))
          .map { result =>
            val _ = akka.pattern.after(expiration, system.scheduler)(backend.delete(client))
            result
          }

      override def delete(client: Client.Id): Future[Boolean] =
        backend.delete(client)

      override def get(client: Client.Id): Future[Option[StoredAuthorizationCode]] =
        backend.get(client)

      override def codes: Future[Map[Client.Id, StoredAuthorizationCode]] =
        backend.entries
    }
}
