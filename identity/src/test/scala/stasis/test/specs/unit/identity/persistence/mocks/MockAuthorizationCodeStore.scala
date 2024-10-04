package stasis.test.specs.unit.identity.persistence.mocks

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.identity.model.codes.AuthorizationCode
import stasis.identity.model.codes.StoredAuthorizationCode
import stasis.identity.persistence.codes.AuthorizationCodeStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class MockAuthorizationCodeStore(
  underlying: KeyValueStore[AuthorizationCode, StoredAuthorizationCode]
)(implicit system: ActorSystem[Nothing])
    extends AuthorizationCodeStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def put(storedCode: StoredAuthorizationCode): Future[Done] = underlying.put(storedCode.code, storedCode)

  override def delete(code: AuthorizationCode): Future[Boolean] = underlying.delete(code)

  override def get(code: AuthorizationCode): Future[Option[StoredAuthorizationCode]] = underlying.get(code)

  override def all: Future[Seq[StoredAuthorizationCode]] = underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockAuthorizationCodeStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): MockAuthorizationCodeStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()
    new MockAuthorizationCodeStore(underlying =
      MemoryStore[AuthorizationCode, StoredAuthorizationCode](name = s"mock-code-store-${java.util.UUID.randomUUID()}")
    )
  }
}
