package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.{ActorSystem, SpawnProtocol}
import org.apache.pekko.util.Timeout
import stasis.core.persistence.backends.memory.MemoryBackend
import stasis.core.telemetry.TelemetryContext
import stasis.server.model.datasets.DatasetEntryStore
import stasis.shared.model.datasets.DatasetEntry

object MockDatasetEntryStore {
  def apply()(implicit
    system: ActorSystem[SpawnProtocol.Command],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DatasetEntryStore = {
    val backend: MemoryBackend[DatasetEntry.Id, DatasetEntry] =
      MemoryBackend[DatasetEntry.Id, DatasetEntry](
        s"mock-dataset-entry-store-${java.util.UUID.randomUUID()}"
      )

    DatasetEntryStore(backend)(system.executionContext)
  }
}
