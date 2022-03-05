package stasis.client_android.activities.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import stasis.client_android.scheduling.SchedulerService

class SystemStartedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(
                Intent(context, SchedulerService::class.java)
            )
        }
    }
}
