package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import scala.concurrent.duration._

import stasis.shared.api.requests.CreateDatasetDefinition
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.test.specs.unit.UnitSpec

class CreateDatasetDefinitionSpec extends UnitSpec {
  it should "convert requests to definitions" in {
    val now = Instant.now()

    val expectedDefinition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      info = "test-definition",
      device = Device.generateId(),
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
      removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, duration = 1.second),
      created = now,
      updated = now
    )

    val request = CreateDatasetDefinition(
      info = expectedDefinition.info,
      device = expectedDefinition.device,
      redundantCopies = expectedDefinition.redundantCopies,
      existingVersions = expectedDefinition.existingVersions,
      removedVersions = expectedDefinition.removedVersions
    )

    request.toDefinition.copy(
      id = expectedDefinition.id,
      created = expectedDefinition.created,
      updated = expectedDefinition.updated
    ) should be(expectedDefinition)
  }
}
