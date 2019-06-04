package stasis.test.specs.unit.shared.api.requests

import scala.concurrent.duration._

import stasis.shared.api.requests.UpdateDatasetDefinition
import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

class UpdateDatasetDefinitionSpec extends UnitSpec {
  it should "convert requests to updated definitions" in {
    val initialDefinition = DatasetDefinition(
      id = DatasetDefinition.generateId(),
      device = Device.generateId(),
      schedule = None,
      redundantCopies = 1,
      existingVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.All, duration = 1.second),
      removedVersions = DatasetDefinition.Retention(DatasetDefinition.Retention.Policy.LatestOnly, duration = 1.second)
    )

    val expectedDefinition = initialDefinition.copy(
      schedule = Some(Schedule.generateId()),
      redundantCopies = initialDefinition.redundantCopies + 1
    )

    val request = UpdateDatasetDefinition(
      schedule = expectedDefinition.schedule,
      redundantCopies = expectedDefinition.redundantCopies,
      existingVersions = expectedDefinition.existingVersions,
      removedVersions = expectedDefinition.removedVersions
    )

    request.toUpdatedDefinition(initialDefinition) should be(expectedDefinition)
  }
}
