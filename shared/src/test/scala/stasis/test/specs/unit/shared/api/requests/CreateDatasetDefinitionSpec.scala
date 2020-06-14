package stasis.test.specs.unit.shared.api.requests

import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class CreateDatasetDefinitionSpec extends UnitSpec {
  it should "convert requests to definitions" in {
    val expectedDefinition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "test-definition",
      device = Device.generateId(),
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
      removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, duration = 1.second)
    )

    val request = CreateDatasetDefinition(
      info = expectedDefinition.info,
      device = expectedDefinition.device,
      redundantCopies = expectedDefinition.redundantCopies,
      existingVersions = expectedDefinition.existingVersions,
      removedVersions = expectedDefinition.removedVersions
    )

    request.toDefinition.copy(id = expectedDefinition.id) should be(expectedDefinition)
  }
}
