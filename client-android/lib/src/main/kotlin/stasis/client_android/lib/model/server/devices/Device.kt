package stasis.client_android.lib.model.server.devices

import stasis.client_android.lib.model.core.NodeId
import stasis.client_android.lib.model.server.users.UserId
import java.math.BigInteger
import java.time.Duration
import java.util.UUID

data class Device(
    val id: DeviceId,
    val node: NodeId,
    val owner: UserId,
    val active: Boolean,
    val limits: Limits?
) {
    data class Limits(
        val maxCrates: Long,
        val maxStorage: BigInteger,
        val maxStoragePerCrate: BigInteger,
        val maxRetention: Duration,
        val minRetention: Duration
    )
}

typealias DeviceId = UUID
