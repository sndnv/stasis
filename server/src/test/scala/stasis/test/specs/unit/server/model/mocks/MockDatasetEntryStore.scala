package stasis.test.specs.unit.server.model.mocks

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.server.model.datasets.DatasetEntryStore
import stasis.shared.model.datasets.DatasetEntry

object MockDatasetEntryStore {
  def apply()(implicit system: ActorSystem[SpawnProtocol.Command], timeout: Timeout): DatasetEntryStore = {
    val backend: MemoryBackend[DatasetEntry.Id, DatasetEntry] =
      MemoryBackend[DatasetEntry.Id, DatasetEntry](
        s"mock-dataset-entry-store-${java.util.UUID.randomUUID()}"
      )

    DatasetEntryStore(backend)(system.executionContext)
  }
}
