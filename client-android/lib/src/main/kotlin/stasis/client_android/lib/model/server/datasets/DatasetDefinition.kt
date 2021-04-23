package stasis.client_android.lib.model.server.datasets

import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.server.devices.DeviceId
import java.time.Duration
import java.util.UUID

@JsonClass(generateAdapter = true)
data class DatasetDefinition(
    val id: DatasetDefinitionId,
    val info: String,
    val device: DeviceId,
    val redundantCopies: Int,
    val existingVersions: Retention,
    val removedVersions: Retention
) {
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
