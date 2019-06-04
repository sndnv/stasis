package stasis.shared.api.requests

import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device
import stasis.shared.model.schedules.Schedule

final case class CreateDatasetDefinition(
  device: Device.Id,
  schedule: Option[Schedule.Id],
  redundantCopies: Int,
  existingVersions: DatasetDefinition.Retention,
  removedVersions: DatasetDefinition.Retention
)

object CreateDatasetDefinition {
  implicit class RequestToDefinition(request: CreateDatasetDefinition) {
    def toDefinition: DatasetDefinition =
      DatasetDefinition(
        id = DatasetDefinition.generateId(),
        device = request.device,
        schedule = request.schedule,
        redundantCopies = request.redundantCopies,
        existingVersions = request.existingVersions,
        removedVersions = request.removedVersions
      )
  }
}
