package stasis.client_android.lib.model.server.datasets

import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.devices.DeviceId
import java.time.Instant
import java.util.UUID

data class DatasetEntry(
    val id: DatasetEntryId,
    val definition: DatasetDefinitionId,
    val device: DeviceId,
    val data: Set<CrateId>,
    val metadata: CrateId,
    val created: Instant
)

typealias DatasetEntryId = UUID
