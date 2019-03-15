package stasis.server.model.datasets

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.server.model.devices.Device

final case class DatasetEntry(
  id: DatasetEntry.Id,
  definition: DatasetDefinition.Id,
  device: Device.Id,
  data: Set[Crate.Id],
  created: Instant
)

object DatasetEntry {
  type Id = java.util.UUID

  def generateId(): Id = java.util.UUID.randomUUID()
}
