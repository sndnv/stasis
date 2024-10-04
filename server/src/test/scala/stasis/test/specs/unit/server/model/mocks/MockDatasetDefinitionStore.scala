package stasis.test.specs.unit.server.model.mocks

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.util.Timeout

import stasis.layers.persistence.memory.MemoryStore
import stasis.layers.telemetry.TelemetryContext
import stasis.server.model.datasets.DatasetDefinitionStore
import stasis.shared.model.datasets.DatasetDefinition

object MockDatasetDefinitionStore {
  def apply()(implicit
    system: ActorSystem[Nothing],
    telemetry: TelemetryContext,
    timeout: Timeout
  ): DatasetDefinitionStore = {
    val backend: MemoryStore[DatasetDefinition.Id, DatasetDefinition] =
      MemoryStore[DatasetDefinition.Id, DatasetDefinition](
        s"mock-dataset-definition-store-${java.util.UUID.randomUUID()}"
      )

    DatasetDefinitionStore(backend)(system.executionContext)
  }
}
