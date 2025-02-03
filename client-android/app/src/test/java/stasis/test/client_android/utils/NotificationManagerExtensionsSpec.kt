package stasis.test.client_android.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.ops.Operation
import stasis.client_android.lib.ops.exceptions.OperationRestrictedFailure
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.utils.NotificationManagerExtensions
import stasis.client_android.utils.NotificationManagerExtensions.createSchedulingNotificationChannels
import stasis.client_android.utils.NotificationManagerExtensions.putActiveScheduleNotFoundNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationCompletedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putOperationStartedNotification
import stasis.client_android.utils.NotificationManagerExtensions.putPublicScheduleNotFoundNotification
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class NotificationManagerExtensionsSpec {
    @Test
    fun createForegroundServiceNotifications() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns "test"
        every { context.packageName } returns "stasis.client_android.scheduling"

        val (id, notification) = NotificationManagerExtensions.createForegroundServiceNotification(
            context
        )

        assertThat(id, equalTo(-1))
        assertThat(
            notification.group,
            equalTo("stasis.client_android.scheduling.foreground_service_notification")
        )
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE), equalTo("test"))
    }

    @Test
    fun createSchedulingNotificationChannels() {
        val channels = mutableListOf<NotificationChannel>()

        val context = mockk<Context>()
        every { context.getString(any()) } returns "test"

        val manager = mockk<NotificationManager>()
        justRun { manager.createNotificationChannel(capture(channels)) }

        manager.createSchedulingNotificationChannels(context)

        assertThat(channels.toList().size, equalTo(2))
    }

    @Test
    fun createPublicScheduleNotFoundNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putPublicScheduleNotFoundNotification(context, schedule)

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TITLE),
            containsString("Missing schedule")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TEXT),
            containsString("Expiration operation is not associated with a valid schedule")
        )
    }

    @Test
    fun createActiveScheduleNotFoundNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putActiveScheduleNotFoundNotification(context, schedule.id)

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TITLE),
            containsString("Invalid scheduled operation")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TEXT),
            containsString("Attempted to execute a schedule with ID [0] but it was not available")
        )
    }

    @Test
    fun createOperationStartedNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putOperationStartedNotification(context, schedule)

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TITLE),
            containsString("Expiration started")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TEXT),
            containsString("Running a new expiration operation")
        )
    }

    @Test
    fun createOperationCompletedSuccessfullyNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putOperationCompletedNotification(
            context = context,
            activeSchedule = schedule,
            failure = null
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TITLE),
            containsString("Expiration completed")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TEXT),
            containsString("Expiration operation completed successfully")
        )
    }

    @Test
    fun createOperationFailedNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putOperationCompletedNotification(
            context = context,
            activeSchedule = schedule,
            failure = RuntimeException("Test failure")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TITLE),
            containsString("Expiration operation failed")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TEXT),
            containsString("Test failure")
        )
    }

    @Test
    fun createOperationFailedWithRestrictionsNotifications() {
        val notification = slot<Notification>()

        val context = ApplicationProvider.getApplicationContext<Context>()

        val manager = mockk<NotificationManager>()
        justRun { manager.notify(any(), capture(notification)) }

        manager.putOperationCompletedNotification(
            context = context,
            activeSchedule = schedule,
            failure = OperationRestrictedFailure(restrictions = listOf(Operation.Restriction.LimitedNetwork))
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TITLE),
            containsString("Expiration operation failed")
        )

        assertThat(
            notification.captured.extras.getString(Notification.EXTRA_TEXT),
            containsString("Operation could not be started: restricted or metered network")
        )
    }

    private val schedule = ActiveSchedule(
        id = 0,
        assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
    )
}
