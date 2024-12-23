package stasis.client_android.lib.model.server.devices

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import stasis.client_android.lib.model.core.NodeId
import stasis.client_android.lib.model.server.users.UserId
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class Device(
    val id: DeviceId,
    val name: String,
    val node: NodeId,
    val owner: UserId,
    val active: Boolean,
    val limits: Limits?,
    val created: Instant,
    val updated: Instant
) {
    @JsonClass(generateAdapter = true)
    data class Limits(
        @Json(name = "max_crates")
        val maxCrates: Long,
        @Json(name = "max_storage")
        val maxStorage: BigInteger,
        @Json(name = "max_storage_per_crate")
        val maxStoragePerCrate: BigInteger,
        @Json(name = "max_retention")
        val maxRetention: Duration,
        @Json(name = "min_retention")
        val minRetention: Duration
    )
}

typealias DeviceId = UUID
