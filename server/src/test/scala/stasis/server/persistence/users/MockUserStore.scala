package stasis.server.persistence.users

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext
import stasis.shared.model.users.User

class MockUserStore(
  userSaltSize: Int,
  underlying: KeyValueStore[User.Id, User]
)(implicit system: ActorSystem[Nothing])
    extends UserStore {
  override protected implicit val ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[users] def put(user: User): Future[Done] = underlying.put(user.id, user)

  override protected[users] def delete(user: User.Id): Future[Boolean] = underlying.delete(user)

  override protected[users] def get(user: User.Id): Future[Option[User]] = underlying.get(user)

  override protected[users] def list(): Future[Seq[User]] = underlying.entries.map(_.values.toSeq)

  override protected[users] def generateSalt(): String = {
    val rnd: Random = ThreadLocalRandom.current()
    rnd.alphanumeric.take(userSaltSize).mkString
  }

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockUserStore {
  def apply(userSaltSize: Int = 8)(implicit system: ActorSystem[Nothing], timeout: Timeout): UserStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val underlying = MemoryStore[User.Id, User](s"mock-user-store-${java.util.UUID.randomUUID()}")

    new MockUserStore(userSaltSize = userSaltSize, underlying = underlying)
  }
}
