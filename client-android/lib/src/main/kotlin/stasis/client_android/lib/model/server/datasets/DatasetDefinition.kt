package stasis.client_android.lib.model.server.datasets

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.server.devices.DeviceId
import java.time.Duration
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DatasetDefinition(
    val id: DatasetDefinitionId,
    val info: String,
    val device: DeviceId,
    @field:Json(name = "redundant_copies")
    val redundantCopies: Int,
    @field:Json(name = "existing_versions")
    val existingVersions: Retention,
    @field:Json(name = "removed_versions")
    val removedVersions: Retention,
    val created: Instant,
    val updated: Instant
) {
    @JsonClass(generateAdapter = true)
    data class Retention(
        val policy: Policy,
        val duration: Duration
    ) {
        sealed class Policy {
            data class AtMost(val versions: Int) : Policy()
            object LatestOnly : Policy()
            object All : Policy()
        }
    }
}

typealias DatasetDefinitionId = UUID
