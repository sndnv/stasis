package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.shared.api.requests.CreateDatasetEntry
import stasis.shared.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.UnitSpec

class CreateDatasetEntrySpec extends UnitSpec {
  it should "convert requests to entries" in {
    val expectedEntry = DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = Set(Crate.generateId(), Crate.generateId()),
      metadata = Crate.generateId(),
      created = Instant.now()
    )

    val request = CreateDatasetEntry(
      definition = expectedEntry.definition,
      device = expectedEntry.device,
      metadata = expectedEntry.metadata,
      data = expectedEntry.data
    )

    request.toEntry.copy(id = expectedEntry.id, created = expectedEntry.created) should be(expectedEntry)
  }
}
