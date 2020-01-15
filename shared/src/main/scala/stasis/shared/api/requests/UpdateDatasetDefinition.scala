package stasis.shared.api.requests

import stasis.shared.model.datasets.DatasetDefinition

final case class UpdateDatasetDefinition(
  info: String,
  redundantCopies: Int,
  existingVersions: DatasetDefinition.Retention,
  removedVersions: DatasetDefinition.Retention
)

object UpdateDatasetDefinition {
  implicit class RequestToUpdatedDefinition(request: UpdateDatasetDefinition) {
    def toUpdatedDefinition(definition: DatasetDefinition): DatasetDefinition =
      definition.copy(
        info = request.info,
        redundantCopies = request.redundantCopies,
        existingVersions = request.existingVersions,
        removedVersions = request.removedVersions
      )
  }
}
