package stasis.client_android.lib.model.server.users

import com.squareup.moshi.JsonClass
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.UUID

@JsonClass(generateAdapter = true)
data class User(
    val id: UserId,
    val salt: String,
    val active: Boolean,
    val limits: Limits?,
    val permissions: Set<String>,
    val created: Instant,
    val updated: Instant
) {
    @JsonClass(generateAdapter = true)
    data class Limits(
        val maxDevices: Long,
        val maxCrates: Long,
        val maxStorage: BigInteger,
        val maxStoragePerCrate: BigInteger,
        val maxRetention: Duration,
        val minRetention: Duration
    )
}

typealias UserId = UUID
