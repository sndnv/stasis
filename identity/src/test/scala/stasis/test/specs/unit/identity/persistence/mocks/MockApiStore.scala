package stasis.test.specs.unit.identity.persistence.mocks

import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.identity.model.apis.Api
import stasis.identity.persistence.apis.ApiStore
import stasis.layers.persistence.KeyValueStore
import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.persistence.migration.Migration
import stasis.layers.telemetry.MockTelemetryContext
import stasis.layers.telemetry.TelemetryContext

class MockApiStore(
  underlying: KeyValueStore[Api.Id, Api]
)(implicit system: ActorSystem[Nothing])
    extends ApiStore {
  import system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override def put(api: Api): Future[Done] = underlying.put(api.id, api)

  override def delete(api: Api.Id): Future[Boolean] = underlying.delete(api)

  override def get(api: Api.Id): Future[Option[Api]] = underlying.get(api)

  override def all: Future[Seq[Api]] = underlying.entries.map(_.values.toSeq)

  override def contains(api: Api.Id): Future[Boolean] = underlying.contains(api)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockApiStore {
  def apply()(implicit system: ActorSystem[Nothing], timeout: Timeout): MockApiStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    new MockApiStore(
      underlying = MemoryStore[Api.Id, Api](name = s"mock-api-store-${java.util.UUID.randomUUID()}")
    )
  }
}
