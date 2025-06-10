package stasis.client_android.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.serialization.Extras.putActiveScheduleId
import java.time.Instant

object AlarmManagerExtensions {
    fun AlarmManager.putScheduleAlarm(
        context: Context,
        schedule: ActiveSchedule,
        instant: Instant
    ) {
        set(
            AlarmManager.RTC,
            instant.toEpochMilli(),
            context.getAlarmIntent(schedule)
        )
    }

    fun AlarmManager.deleteScheduleAlarm(context: Context, schedule: ActiveSchedule) {
        cancel(context.getAlarmIntent(schedule))
    }

    private fun Context.getAlarmIntent(forSchedule: ActiveSchedule): PendingIntent {
        return PendingIntent.getService(
            this,
            forSchedule.id.toInt(),
            Intent(this, SchedulerService::class.java).apply {
                action = SchedulerService.ActionExecuteSchedule
                putActiveScheduleId(
                    SchedulerService.ActionExecuteScheduleExtraActiveScheduleId,
                    forSchedule.id
                )
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
