package stasis.shared.api.requests

import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.devices.Device

final case class CreateDatasetDefinition(
  info: String,
  device: Device.Id,
  redundantCopies: Int,
  existingVersions: DatasetDefinition.Retention,
  removedVersions: DatasetDefinition.Retention
)

object CreateDatasetDefinition {
  implicit class RequestToDefinition(request: CreateDatasetDefinition) {
    def toDefinition: DatasetDefinition =
      DatasetDefinition(
        id = DatasetDefinition.generateId(),
        info = request.info,
        device = request.device,
        redundantCopies = request.redundantCopies,
        existingVersions = request.existingVersions,
        removedVersions = request.removedVersions
      )
  }
}
