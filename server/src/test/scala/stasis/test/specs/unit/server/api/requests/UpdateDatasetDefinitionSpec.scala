package stasis.test.specs.unit.server.api.requests

import stasis.server.api.requests.UpdateDatasetDefinition
import stasis.server.model.datasets.DatasetDefinition
import stasis.server.model.devices.Device
import stasis.server.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

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
