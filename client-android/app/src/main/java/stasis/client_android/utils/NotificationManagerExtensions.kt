package stasis.client_android.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.annotation.IdRes
import androidx.core.app.NotificationCompat
import androidx.navigation.NavDeepLinkBuilder
import stasis.client_android.R
import stasis.client_android.activities.MainActivity
import stasis.client_android.activities.helpers.Common.asRestrictionsString
import stasis.client_android.activities.helpers.Common.toAssignmentTypeString
import stasis.client_android.lib.ops.exceptions.OperationRestrictedFailure
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.scheduling.SchedulerService
import java.util.Locale

object NotificationManagerExtensions {
    fun createForegroundServiceNotification(
        context: Context,
        config: Config = Config.Default
    ): Pair<Int, Notification> {
        val pendingIntent: PendingIntent = Intent(context, SchedulerService::class.java).let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notificationId = -1

        val notification = Notification.Builder(context, config.foregroundServiceChannel.id)
            .setContentTitle(context.getString(R.string.notification_foreground_service_title))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setGroup("stasis.client_android.scheduling.foreground_service_notification")
            .build()

        return notificationId to notification
    }

    fun NotificationManager.createSchedulingNotificationChannels(
        context: Context,
        config: Config = Config.Default
    ) {
        createNotificationChannel(
            config.foregroundServiceChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_foreground_service_name)
            )
        )

        createNotificationChannel(
            config.schedulingChannel.toNotificationChannel(
                name = context.getString(R.string.notification_channel_scheduling_name)
            )
        )
    }

    fun NotificationManager.putPublicScheduleNotFoundNotification(
        context: Context,
        activeSchedule: ActiveSchedule,
        config: Config = Config.Default
    ) {
        val title = context.getString(
            R.string.notification_public_schedule_not_found_title
        )

        val text = context.getString(
            R.string.notification_public_schedule_not_found_text,
            activeSchedule.assignment.toAssignmentTypeString(context)
        )

        val notification = NotificationCompat.Builder(context, config.schedulingChannel.id)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(createDeepLink(context, R.id.schedulesFragment))
            .setAutoCancel(true)
            .build()

        notify(activeSchedule.id.toInt(), notification)
    }

    fun NotificationManager.putActiveScheduleNotFoundNotification(
        context: Context,
        activeScheduleId: Long,
        config: Config = Config.Default
    ) {
        val title = context.getString(
            R.string.notification_active_schedule_not_found_title
        )

        val text = context.getString(
            R.string.notification_active_schedule_not_found_text,
            activeScheduleId.toString()
        )

        val notification = NotificationCompat.Builder(context, config.schedulingChannel.id)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(createDeepLink(context, R.id.schedulesFragment))
            .setAutoCancel(true)
            .build()

        notify(activeScheduleId.toInt(), notification)
    }

    fun NotificationManager.putOperationStartedNotification(
        context: Context,
        activeSchedule: ActiveSchedule,
        config: Config = Config.Default
    ) {
        putOperationStartedNotification(
            context = context,
            id = activeSchedule.id.toInt(),
            operation = activeSchedule.assignment.toAssignmentTypeString(context),
            config = config
        )
    }

    fun NotificationManager.putOperationCompletedNotification(
        context: Context,
        activeSchedule: ActiveSchedule,
        failure: Throwable?,
        config: Config = Config.Default
    ) {
        putOperationCompletedNotification(
            context = context,
            id = activeSchedule.id.toInt(),
            operation = activeSchedule.assignment.toAssignmentTypeString(context),
            failure = failure,
            config = config
        )
    }

    fun NotificationManager.putOperationStartedNotification(
        context: Context,
        id: Int,
        operation: String,
        config: Config = Config.Default
    ) {
        val title = context.getString(
            R.string.notification_operation_started_title,
            operation
        )

        val text = context.getString(
            R.string.notification_operation_started_text,
            operation.lowercase(Locale.getDefault())
        )

        val notification = NotificationCompat.Builder(context, config.schedulingChannel.id)
            .setSmallIcon(R.drawable.ic_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(createDeepLink(context, R.id.operationsFragment))
            .setAutoCancel(true)
            .setProgress(0, 0, true)
            .build()

        notify(id, notification)
    }

    fun NotificationManager.putOperationCompletedNotification(
        context: Context,
        id: Int,
        operation: String,
        failure: Throwable?,
        config: Config = Config.Default
    ) {
        val (icon, title, text) = if (failure == null) {
            val icon = R.drawable.ic_check
            val title = context.getString(R.string.notification_operation_completed_title, operation)
            val text = context.getString(R.string.notification_operation_completed_text, operation)

            Triple(icon, title, text)
        } else {
            val icon = R.drawable.ic_close
            val title = context.getString(R.string.notification_operation_failed_title, operation)
            val text = when (failure) {
                is OperationRestrictedFailure -> context.getString(
                    R.string.notification_operation_failed_with_restrictions_text,
                    failure.restrictions.asRestrictionsString(context)
                )

                else -> context.getString(
                    R.string.notification_operation_failed_text,
                    failure.message ?: failure.javaClass.simpleName
                )
            }

            Triple(icon, title, text)
        }

        val notification = NotificationCompat.Builder(context, config.schedulingChannel.id)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(createDeepLink(context, R.id.operationsFragment))
            .setAutoCancel(true)
            .build()

        notify(id, notification)
    }

    private fun createDeepLink(context: Context, @IdRes destination: Int): PendingIntent =
        NavDeepLinkBuilder(context)
            .setGraph(R.navigation.main_nav_graph)
            .setDestination(destination)
            .setComponentName(MainActivity::class.java)
            .createPendingIntent()


    data class Config(
        val foregroundServiceChannel: Channel,
        val schedulingChannel: Channel
    ) {
        companion object {
            val Default: Config = Config(
                foregroundServiceChannel = Channel.ForegroundServiceChannel,
                schedulingChannel = Channel.SchedulingChannel
            )
        }

        data class Channel(
            val id: String,
            val importance: Int,
            val light: Int?,
            val vibrationEnabled: Boolean
        ) {
            fun toNotificationChannel(name: String): NotificationChannel {
                val channel = NotificationChannel(id, name, importance)

                channel.enableLights(light != null)
                channel.lightColor = light ?: Color.TRANSPARENT
                channel.enableVibration(vibrationEnabled)

                return channel
            }

            companion object Defaults {
                val ForegroundServiceChannel: Channel = Channel(
                    id = "stasis.client_android.scheduling.notification_channel_foreground_service",
                    importance = NotificationManager.IMPORTANCE_LOW,
                    light = null,
                    vibrationEnabled = false
                )

                val SchedulingChannel: Channel = Channel(
                    id = "stasis.client_android.scheduling.notification_channel_scheduling",
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    light = null,
                    vibrationEnabled = false
                )
            }
        }
    }
}
