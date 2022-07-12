package stasis.client_android.tracking

import androidx.lifecycle.LiveData
import stasis.client_android.lib.tracking.ServerTracker

interface ServerTrackerView {
    val state: LiveData<Map<String, ServerTracker.ServerState>>
    fun updates(server: String): LiveData<ServerTracker.ServerState>
}
