package stasis.client_android.lib.tracking

import java.time.Instant

interface ServerTracker {
    fun reachable(server: String)
    fun unreachable(server: String)

    data class ServerState(
        val reachable: Boolean,
        val timestamp: Instant
    )
}
