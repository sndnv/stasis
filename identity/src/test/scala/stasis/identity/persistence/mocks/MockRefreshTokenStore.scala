package stasis.identity.persistence.mocks

import java.time.Instant

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.identity.model.clients.Client
import stasis.identity.model.owners.ResourceOwner
import stasis.identity.model.tokens.RefreshToken
import stasis.identity.model.tokens.StoredRefreshToken
import stasis.identity.persistence.tokens.RefreshTokenStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class MockRefreshTokenStore(
  underlying: KeyValueStore[RefreshToken, StoredRefreshToken]
)(implicit system: ActorSystem[Nothing])
    extends RefreshTokenStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def put(client: Client.Id, token: RefreshToken, owner: ResourceOwner, scope: Option[String]): Future[Done] = {
    val storedToken = StoredRefreshToken(
      token = token,
      client = client,
      owner = owner.username,
      scope = scope,
      expiration = Instant.now().plusSeconds(30),
      created = Instant.now()
    )

    underlying.put(token, storedToken)
  }

  override def delete(code: RefreshToken): Future[Boolean] = underlying.delete(code)

  override def get(code: RefreshToken): Future[Option[StoredRefreshToken]] = underlying.get(code)

  override def all: Future[Seq[StoredRefreshToken]] = underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockRefreshTokenStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): MockRefreshTokenStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()
    new MockRefreshTokenStore(
      underlying = MemoryStore[RefreshToken, StoredRefreshToken](name = s"mock-code-store-${java.util.UUID.randomUUID()}")
    )
  }
}
