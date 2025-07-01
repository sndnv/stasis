package stasis.identity.persistence.mocks

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.identity.model.owners.ResourceOwner
import stasis.identity.persistence.owners.ResourceOwnerStore
import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.telemetry.TelemetryContext

class MockResourceOwnerStore(underlying: KeyValueStore[ResourceOwner.Id, ResourceOwner])(implicit system: ActorSystem[Nothing])
    extends ResourceOwnerStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def put(owner: ResourceOwner): Future[Done] = underlying.put(owner.username, owner)

  override def delete(owner: ResourceOwner.Id): Future[Boolean] = underlying.delete(owner)

  override def get(owner: ResourceOwner.Id): Future[Option[ResourceOwner]] = underlying.get(owner)

  override def all: Future[Seq[ResourceOwner]] = underlying.entries.map(_.values.toSeq)

  override def contains(owner: ResourceOwner.Id): Future[Boolean] = underlying.contains(owner)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockResourceOwnerStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): MockResourceOwnerStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    new MockResourceOwnerStore(underlying =
      MemoryStore[ResourceOwner.Id, ResourceOwner](name = s"mock-owner-store-${java.util.UUID.randomUUID()}")
    )
  }
}
