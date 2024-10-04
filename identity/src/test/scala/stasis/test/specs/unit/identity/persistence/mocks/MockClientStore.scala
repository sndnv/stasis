package stasis.test.specs.unit.identity.persistence.mocks

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.identity.model.clients.Client
import stasis.identity.persistence.clients.ClientStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class MockClientStore(underlying: KeyValueStore[Client.Id, Client])(implicit system: ActorSystem[Nothing]) extends ClientStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def put(client: Client): Future[Done] = underlying.put(client.id, client)

  override def delete(client: Client.Id): Future[Boolean] = underlying.delete(client)

  override def get(client: Client.Id): Future[Option[Client]] = underlying.get(client)

  override def all: Future[Seq[Client]] = underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockClientStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): MockClientStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()
    new MockClientStore(underlying = MemoryStore[Client.Id, Client](name = s"mock-client-store-${java.util.UUID.randomUUID()}"))
  }
}
