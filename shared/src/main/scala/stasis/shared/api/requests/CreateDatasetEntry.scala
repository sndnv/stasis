package stasis.shared.api.requests

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device

final case class CreateDatasetEntry(
  definition: DatasetDefinition.Id,
  device: Device.Id,
  data: Set[Crate.Id],
  metadata: Crate.Id
)

object CreateDatasetEntry {
  implicit class RequestToEntry(request: CreateDatasetEntry) {
    def toEntry: DatasetEntry =
      DatasetEntry(
        id = DatasetEntry.generateId(),
        definition = request.definition,
        device = request.device,
        data = request.data,
        metadata = request.metadata,
        created = Instant.now()
      )
  }
}
