package stasis.client_android.tracking

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import stasis.client_android.lib.tracking.ServerTracker
import java.time.Instant

class DefaultServerTracker(
    looper: Looper
) : ServerTracker, ServerTrackerView {
    private val trackedState: MutableLiveData<Map<String, ServerTracker.ServerState>> =
        MutableLiveData(emptyMap())

    private val handler: TrackingHandler = TrackingHandler(looper)

    override val state: LiveData<Map<String, ServerTracker.ServerState>> = trackedState

    override fun updates(server: String): LiveData<ServerTracker.ServerState> =
        MediatorLiveData<ServerTracker.ServerState>().apply {
            addSource(trackedState) { servers ->
                servers[server]?.let { state ->
                    postValue(state)
                }
            }
        }

    override fun reachable(server: String) {
        send(event = TrackingEvent.ServerReachable(server = server))
    }

    override fun unreachable(server: String) {
        send(event = TrackingEvent.ServerUnreachable(server = server))
    }

    private fun send(event: TrackingEvent) {
        handler.obtainMessage().let { msg ->
            msg.obj = event
            handler.sendMessage(msg)
        }
    }

    private inner class TrackingHandler(looper: Looper) : Handler(looper) {
        private var _state: Map<String, ServerTracker.ServerState> = emptyMap()

        override fun handleMessage(msg: Message) {
            Log.v(TAG, "Received tracking event [${msg.obj}]")

            _state = when (val message = msg.obj) {
                is TrackingEvent.ServerReachable -> _state + (message.server to ServerTracker.ServerState(
                    reachable = true,
                    timestamp = Instant.now()
                ))

                is TrackingEvent.ServerUnreachable -> _state + (message.server to ServerTracker.ServerState(
                    reachable = false,
                    timestamp = Instant.now()
                ))

                else -> throw IllegalArgumentException("Unexpected message encountered: [$message]")
            }

            trackedState.postValue(_state)
        }
    }

    private sealed class TrackingEvent {
        data class ServerReachable(val server: String) : TrackingEvent()
        data class ServerUnreachable(val server: String) : TrackingEvent()
    }

    companion object {
        private const val TAG: String = "DefaultServerTracker"
    }
}
