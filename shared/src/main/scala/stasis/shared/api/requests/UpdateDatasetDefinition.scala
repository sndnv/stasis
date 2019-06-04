package stasis.shared.api.requests

import stasis.shared.model.datasets.DatasetDefinition
import stasis.shared.model.schedules.Schedule

final case class UpdateDatasetDefinition(
  schedule: Option[Schedule.Id],
  redundantCopies: Int,
  existingVersions: DatasetDefinition.Retention,
  removedVersions: DatasetDefinition.Retention
)

object UpdateDatasetDefinition {
  implicit class RequestToUpdatedDefinition(request: UpdateDatasetDefinition) {
    def toUpdatedDefinition(definition: DatasetDefinition): DatasetDefinition =
      definition.copy(
        schedule = request.schedule,
        redundantCopies = request.redundantCopies,
        existingVersions = request.existingVersions,
        removedVersions = request.removedVersions
      )
  }
}
