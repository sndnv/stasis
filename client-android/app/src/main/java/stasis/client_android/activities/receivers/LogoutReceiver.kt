package stasis.client_android.activities.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import stasis.client_android.persistence.credentials.CredentialsViewModel

class LogoutReceiver(
    private val credentials: CredentialsViewModel
) : BroadcastReceiver() {
    val intentFilter: IntentFilter = IntentFilter(Action)

    override fun onReceive(context: Context?, intent: Intent?) {
        credentials.logout { }
    }

    companion object {
        const val Action: String = "stasis.client_android.activities.receivers.Logout"
    }
}
