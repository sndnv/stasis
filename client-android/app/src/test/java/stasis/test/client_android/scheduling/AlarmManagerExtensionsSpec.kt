package stasis.test.client_android.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import io.mockk.confirmVerified
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.scheduling.AlarmManagerExtensions.putScheduleAlarm
import stasis.client_android.scheduling.AlarmManagerExtensions.deleteScheduleAlarm
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class AlarmManagerExtensionsSpec {
    @Test
    fun createEvaluationAlarms() {
        val schedule = ActiveSchedule(
            id = 0,
            assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
        )

        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        justRun { manager.set(any(), any(), any()) }

        manager.putScheduleAlarm(context, schedule, instant = Instant.now().plusSeconds(42))

        verify(exactly = 1) { manager.set(any(), any(), any()) }

        confirmVerified(manager)
    }

    @Test
    fun removeEvaluationAlarms() {
        val schedule = ActiveSchedule(
            id = 0,
            assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
        )

        val context = mockk<Context>(relaxed = true)

        val manager = mockk<AlarmManager>()
        justRun { manager.cancel(any<PendingIntent>()) }

        manager.deleteScheduleAlarm(context, schedule)

        verify(exactly = 1) { manager.cancel(any<PendingIntent>()) }

        confirmVerified(manager)
    }
}
