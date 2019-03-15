package stasis.test.specs.unit.server.api.requests

import stasis.server.api.requests.CreateDatasetDefinition
import stasis.server.model.datasets.DatasetDefinition
import stasis.server.model.devices.Device
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class CreateDatasetDefinitionSpec extends UnitSpec {
  it should "convert requests to definitions" in {
    val expectedDefinition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      device = Device.generateId(),
      schedule = None,
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
      removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, duration = 1.second)
    )

    val request = CreateDatasetDefinition(
      device = expectedDefinition.device,
      schedule = expectedDefinition.schedule,
      redundantCopies = expectedDefinition.redundantCopies,
      existingVersions = expectedDefinition.existingVersions,
      removedVersions = expectedDefinition.removedVersions
    )

    request.toDefinition.copy(id = expectedDefinition.id) should be(expectedDefinition)
  }
}
