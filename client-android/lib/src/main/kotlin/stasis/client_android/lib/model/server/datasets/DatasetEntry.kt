package stasis.client_android.lib.model.server.datasets

import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.devices.DeviceId
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DatasetEntry(
    val id: DatasetEntryId,
    val definition: DatasetDefinitionId,
    val device: DeviceId,
    val data: Set<CrateId>,
    val metadata: CrateId,
    val created: Instant
)

typealias DatasetEntryId = UUID
