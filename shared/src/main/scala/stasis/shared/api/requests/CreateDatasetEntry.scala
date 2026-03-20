package stasis.shared.api.requests

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.datasets.DatasetEntry
import stasis.shared.model.devices.Device

final case class CreateDatasetEntry(
  definition: DatasetDefinition.Id,
  device: Device.Id,
  data: Set[Crate.Id],
  metadata: Crate.Id,
  changes: Option[Long],
  size: Option[Long]
)

object CreateDatasetEntry {
  def apply(
    definition: DatasetDefinition.Id,
    device: Device.Id,
    data: Set[Crate.Id],
    metadata: Crate.Id,
    changes: Long,
    size: Long
  ): CreateDatasetEntry =
    CreateDatasetEntry(
      definition = definition,
      device = device,
      data = data,
      metadata = metadata,
      changes = Some(changes),
      size = Some(size)
    )

  implicit class RequestToEntry(request: CreateDatasetEntry) {
    def toEntry: DatasetEntry =
      DatasetEntry(
        id = DatasetEntry.generateId(),
        definition = request.definition,
        device = request.device,
        data = request.data,
        metadata = request.metadata,
        changes = request.changes,
        size = request.size,
        created = Instant.now()
      )
  }
}
