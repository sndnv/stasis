package stasis.client_android.providers

import android.content.SharedPreferences
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.ServerCoreEndpointClient
import stasis.client_android.lib.ops.monitoring.ServerMonitor
import stasis.client_android.lib.ops.scheduling.OperationExecutor
import stasis.client_android.lib.ops.search.Search
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.utils.Reference
import stasis.client_android.tracking.TrackerView

data class ProviderContext(
    val core: ServerCoreEndpointClient,
    val api: ServerApiEndpointClient,
    val search: Search,
    val executor: OperationExecutor,
    val tracker: TrackerView,
    val credentials: CredentialsProvider,
    val monitor: ServerMonitor
) {
    interface Factory {
        fun getOrCreate(preferences: SharedPreferences): Reference<ProviderContext>
    }
}
