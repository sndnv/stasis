package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import scala.concurrent.duration._

import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.UnitSpec

class UpdateDatasetDefinitionSpec extends UnitSpec {
  it should "convert requests to updated definitions" in {
    val now = Instant.now()

    val initialDefinition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "test-definition",
      device = Device.generateId(),
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
      removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, duration = 1.second),
      created = now,
      updated = now
    )

    val expectedDefinition = initialDefinition.copy(
      redundantCopies = initialDefinition.redundantCopies + 1
    )

    val request = UpdateDatasetDefinition(
      info = expectedDefinition.info,
      redundantCopies = expectedDefinition.redundantCopies,
      existingVersions = expectedDefinition.existingVersions,
      removedVersions = expectedDefinition.removedVersions
    )

    request.toUpdatedDefinition(initialDefinition).copy(updated = expectedDefinition.updated) should be(expectedDefinition)
  }
}
