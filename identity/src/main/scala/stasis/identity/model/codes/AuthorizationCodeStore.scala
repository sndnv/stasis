package stasis.identity.model.codes

import akka.Done
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import stasis.core.persistence.backends.KeyValueBackend

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

trait AuthorizationCodeStore {
  def put(storedCode: StoredAuthorizationCode): Future[Done]
  def delete(code: AuthorizationCode): Future[Boolean]
  def get(code: AuthorizationCode): Future[Option[StoredAuthorizationCode]]
  def codes: Future[Map[AuthorizationCode, StoredAuthorizationCode]]
}

object AuthorizationCodeStore {
  def apply(
    expiration: FiniteDuration,
    backend: KeyValueBackend[AuthorizationCode, StoredAuthorizationCode]
  )(implicit system: ActorSystem[SpawnProtocol.Command]): AuthorizationCodeStore =
    new AuthorizationCodeStore {
      private val untypedSystem = system.classicSystem
      private implicit val ec: ExecutionContext = system.executionContext

      override def put(storedCode: StoredAuthorizationCode): Future[Done] =
        backend
          .put(storedCode.code, storedCode)
          .map { result =>
            val _ = akka.pattern.after(expiration, untypedSystem.scheduler)(backend.delete(storedCode.code))
            result
          }

      override def delete(code: AuthorizationCode): Future[Boolean] =
        backend.delete(code)

      override def get(code: AuthorizationCode): Future[Option[StoredAuthorizationCode]] =
        backend.get(code)

      override def codes: Future[Map[AuthorizationCode, StoredAuthorizationCode]] =
        backend.entries
    }
}
