package stasis.server.api.requests

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.server.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.server.model.devices.Device

final case class CreateDatasetEntry(
  definition: DatasetDefinition.Id,
  device: Device.Id,
  data: Set[Crate.Id]
)

object CreateDatasetEntry {
  implicit class RequestToEntry(request: CreateDatasetEntry) {
    def toEntry: DatasetEntry =
      DatasetEntry(
        id = DatasetEntry.generateId(),
        definition = request.definition,
        device = request.device,
        data = request.data,
        created = Instant.now()
      )
  }
}
