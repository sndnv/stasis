package stasis.client_android.activities.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import stasis.client_android.scheduling.SchedulerService

class SystemConfigurationChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
            || intent.action == Intent.ACTION_TIME_CHANGED
            || intent.action == Intent.ACTION_LOCALE_CHANGED
        ) {
            context.startService(
                Intent(context, SchedulerService::class.java).apply {
                    action = SchedulerService.ActionRefresh
                }
            )
        }
    }
}
