package stasis.test.specs.unit.server.model.mocks

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.users.{User, UserStore}

import scala.concurrent.{ExecutionContext, Future}

class MockUserStore(implicit system: ActorSystem, timeout: Timeout) extends UserStore {
  private val backend: MemoryBackend[User.Id, User] =
    MemoryBackend.untyped[User.Id, User](
      s"mock-user-store-${java.util.UUID.randomUUID()}"
    )

  override protected implicit def ec: ExecutionContext = system.dispatcher

  override protected def create(user: User): Future[Done] =
    backend.put(user.id, user)

  override protected def update(user: User): Future[Done] =
    backend.put(user.id, user)

  override protected def delete(user: User.Id): Future[Boolean] =
    backend.delete(user)

  override protected def get(user: User.Id): Future[Option[User]] =
    backend.get(user)

  override protected def list(): Future[Map[User.Id, User]] =
    backend.entries
}
