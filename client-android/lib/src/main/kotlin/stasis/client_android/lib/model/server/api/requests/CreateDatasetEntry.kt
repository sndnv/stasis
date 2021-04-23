package stasis.client_android.lib.model.server.api.requests

import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.datasets.DatasetDefinitionId
import stasis.client_android.lib.model.server.devices.DeviceId

data class CreateDatasetEntry(
    val definition: DatasetDefinitionId,
    val device: DeviceId,
    val data: Set<CrateId>,
    val metadata: CrateId
)
