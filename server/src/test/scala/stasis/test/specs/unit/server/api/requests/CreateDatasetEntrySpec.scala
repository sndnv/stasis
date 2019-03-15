package stasis.test.specs.unit.server.api.requests

import java.time.Instant

import stasis.core.packaging.Crate
import stasis.server.api.requests.CreateDatasetEntry
import stasis.server.model.datasets.{DatasetDefinition, DatasetEntry}
import stasis.server.model.devices.Device
import stasis.test.specs.unit.UnitSpec

class CreateDatasetEntrySpec extends UnitSpec {
  it should "convert requests to entries" in {
    val expectedEntry = DatasetEntry(
      id = DatasetEntry.generateId(),
      definition = DatasetDefinition.generateId(),
      device = Device.generateId(),
      data = Set(Crate.generateId(), Crate.generateId()),
      created = Instant.now()
    )

    val request = CreateDatasetEntry(
      definition = expectedEntry.definition,
      device = expectedEntry.device,
      data = expectedEntry.data
    )

    request.toEntry.copy(id = expectedEntry.id, created = expectedEntry.created) should be(expectedEntry)
  }
}
