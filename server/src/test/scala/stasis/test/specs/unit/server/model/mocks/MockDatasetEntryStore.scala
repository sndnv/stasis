package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.datasets.DatasetEntryStore
import stasis.shared.model.datasets.DatasetEntry

object MockDatasetEntryStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DatasetEntryStore = {
    val backend: MemoryStore[DatasetEntry.Id, DatasetEntry] =
      MemoryStore[DatasetEntry.Id, DatasetEntry](
        s"mock-dataset-entry-store-${java.util.UUID.randomUUID()}"
      )

    DatasetEntryStore(backend)(system.executionContext)
  }
}
