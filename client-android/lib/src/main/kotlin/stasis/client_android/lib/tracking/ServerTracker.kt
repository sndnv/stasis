package stasis.client_android.lib.tracking

interface ServerTracker {
    fun reachable(server: String)
    fun unreachable(server: String)
}
