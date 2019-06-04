package stasis.shared.model.datasets

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.shared.model.devices.Device

final case class DatasetEntry(
  id: DatasetEntry.Id,
  definition: DatasetDefinition.Id,
  device: Device.Id,
  data: Set[Crate.Id],
  metadata: Crate.Id,
  created: Instant
)

object DatasetEntry {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
