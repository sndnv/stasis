package stasis.server.persistence.datasets

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import io.github.sndnv.layers.persistence.KeyValueStore
import io.github.sndnv.layers.persistence.memory.MemoryStore
import io.github.sndnv.layers.persistence.migration.Migration
import io.github.sndnv.layers.telemetry.mocks.MockTelemetryContext
import io.github.sndnv.layers.telemetry.TelemetryContext
import stasis.shared.model.datasets.DatasetDefinition

class MockDatasetDefinitionStore(
  underlying: KeyValueStore[DatasetDefinition.Id, DatasetDefinition]
)(implicit system: ActorSystem[Nothing])
    extends DatasetDefinitionStore {
  override protected implicit def ec: ExecutionContext = system.executionContext

  override val name: String = underlying.name()

  override val migrations: Seq[Migration] = Seq.empty

  override protected[datasets] def put(definition: DatasetDefinition): Future[Done] =
    underlying.put(definition.id, definition)

  override protected[datasets] def delete(definition: DatasetDefinition.Id): Future[Boolean] =
    underlying.delete(definition)

  override protected[datasets] def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]] =
    underlying.get(definition)

  override protected[datasets] def list(): Future[Seq[DatasetDefinition]] =
    underlying.entries.map(_.values.toSeq)

  override def init(): Future[Done] = underlying.init()

  override def drop(): Future[Done] = underlying.drop()
}

object MockDatasetDefinitionStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    timeout: Timeout
  ): DatasetDefinitionStore = {
    implicit val telemetry: TelemetryContext = MockTelemetryContext()

    val underlying = MemoryStore[DatasetDefinition.Id, DatasetDefinition](
      s"mock-dataset-definition-store-${java.util.UUID.randomUUID()}"
    )

    new MockDatasetDefinitionStore(underlying)
  }
}
