package stasis.client_android.lib.model.server.api.requests

import stasis.client_android.lib.model.server.datasets.DatasetDefinition
import stasis.client_android.lib.model.server.devices.DeviceId

data class CreateDatasetDefinition(
    val info: String,
    val device: DeviceId,
    val redundantCopies: Int,
    val existingVersions: DatasetDefinition.Retention,
    val removedVersions: DatasetDefinition.Retention
)
