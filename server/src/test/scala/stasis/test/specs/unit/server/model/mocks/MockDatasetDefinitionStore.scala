package stasis.test.specs.unit.server.model.mocks

import scala.concurrent.{ExecutionContext, Future}

import akka.Done
import akka.actor.ActorSystem
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.datasets.DatasetDefinitionStore
import stasis.shared.model.datasets.DatasetDefinition

class MockDatasetDefinitionStore()(implicit system: ActorSystem, timeout: Timeout) extends DatasetDefinitionStore {
  private val backend: MemoryBackend[DatasetDefinition.Id, DatasetDefinition] =
    MemoryBackend.untyped[DatasetDefinition.Id, DatasetDefinition](
      s"mock-dataset-definition-store-${java.util.UUID.randomUUID()}"
    )

  override protected implicit def ec: ExecutionContext = system.dispatcher

  override protected def create(definition: DatasetDefinition): Future[Done] =
    backend.put(definition.id, definition)

  override protected def update(definition: DatasetDefinition): Future[Done] =
    backend.put(definition.id, definition)

  override protected def delete(definition: DatasetDefinition.Id): Future[Boolean] =
    backend.delete(definition)

  override protected def get(definition: DatasetDefinition.Id): Future[Option[DatasetDefinition]] =
    backend.get(definition)

  override protected def list(): Future[Map[DatasetDefinition.Id, DatasetDefinition]] =
    backend.entries
}
