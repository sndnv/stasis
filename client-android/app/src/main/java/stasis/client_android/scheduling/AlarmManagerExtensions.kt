package stasis.client_android.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.serialization.Extras.putActiveScheduleId
import java.time.Instant

object AlarmManagerExtensions {
    private const val TAG: String = "AlarmManagerExtensions"

    fun AlarmManager.putScheduleAlarm(
        context: Context,
        schedule: ActiveSchedule,
        instant: Instant
    ) {
        Log.v(
            TAG,
            "Scheduling next alarm for schedule [type=${schedule.assignment.javaClass.simpleName},id=${schedule.id}] at [$instant]"
        )

        set(
            AlarmManager.RTC,
            instant.toEpochMilli(),
            context.getAlarmIntent(schedule)
        )
    }

    fun AlarmManager.deleteScheduleAlarm(context: Context, schedule: ActiveSchedule) {
        Log.v(
            TAG,
            "Removing alarm for schedule [type=${schedule.assignment.javaClass.simpleName},id=${schedule.id}]"
        )

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
