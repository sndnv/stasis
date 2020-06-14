package stasis.test.specs.unit.server.model.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.datasets.DatasetDefinitionStore
import stasis.shared.model.datasets.DatasetDefinition

object MockDatasetDefinitionStore {
  def apply()(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): DatasetDefinitionStore = {
    val backend: MemoryBackend[DatasetDefinition.Id, DatasetDefinition] =
      MemoryBackend[DatasetDefinition.Id, DatasetDefinition](
        s"mock-dataset-definition-store-${java.util.UUID.randomUUID()}"
      )

    DatasetDefinitionStore(backend)(system.executionContext)
  }
}
