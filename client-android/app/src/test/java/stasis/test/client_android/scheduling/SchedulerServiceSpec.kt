package stasis.test.client_android.scheduling

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.scheduling.SchedulerService
import stasis.client_android.scheduling.SchedulerService.Companion.executionDelay
import stasis.client_android.scheduling.SchedulerService.Companion.toSchedulerMessage
import stasis.client_android.serialization.Extras.putActiveSchedule
import stasis.client_android.serialization.Extras.putActiveScheduleId
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SchedulerServiceSpec {
    @Test
    fun convertIntentsToSchedulerMessages() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThat(
            context.createIntent(SchedulerService.ActionRefresh) { it }.toSchedulerMessage(),
            equalTo(SchedulerService.SchedulerMessage.RefreshSchedules)
        )

        assertThat(
            context.createIntent(SchedulerService.ActionCancel) { it }.toSchedulerMessage(),
            equalTo(SchedulerService.SchedulerMessage.CancelSchedules)
        )

        assertThat(
            context.createIntent(SchedulerService.ActionAddSchedule) {
                it.putActiveSchedule(SchedulerService.ActionAddScheduleExtraActiveSchedule, activeSchedule)
            }.toSchedulerMessage(),
            equalTo(SchedulerService.SchedulerMessage.AddSchedule(activeSchedule))
        )

        assertThat(
            context.createIntent(SchedulerService.ActionRemoveSchedule) {
                it.putActiveSchedule(SchedulerService.ActionRemoveScheduleExtraActiveSchedule, activeSchedule)
            }.toSchedulerMessage(),
            equalTo(SchedulerService.SchedulerMessage.RemoveSchedule(activeSchedule))
        )

        assertThat(
            context.createIntent(SchedulerService.ActionExecuteSchedule) {
                it.putActiveScheduleId(SchedulerService.ActionExecuteScheduleExtraActiveScheduleId, activeSchedule.id)
            }.toSchedulerMessage(),
            equalTo(SchedulerService.SchedulerMessage.ExecuteSchedule(activeSchedule.id))
        )

        assertThat(
            null.toSchedulerMessage(),
            equalTo(null)
        )

        try {
            context.createIntent("other") { it }.toSchedulerMessage()
            fail("Excepted failure but none encountered")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected action encountered: [other]"))
        }
    }

    @Test
    fun calculateScheduleExecutionDelay() {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            info = "test-schedule",
            isPublic = true,
            start = LocalDateTime.now(),
            interval = Duration.ofSeconds(42),
            created = Instant.now(),
            updated = Instant.now(),
        )

        val actualDelay = schedule.executionDelay()
        val defaultDelay = schedule.copy(interval = Duration.ofSeconds(1)).executionDelay()

        assertThat(
            actualDelay > schedule.interval.minusSeconds(1).toMillis()
                    && actualDelay < schedule.interval.plusSeconds(1).toMillis(),
            equalTo(true)
        )

        assertThat(
            defaultDelay,
            equalTo(SchedulerService.Companion.Defaults.MinimumExecutionDelay.toMillis())
        )
    }

    private val activeSchedule = ActiveSchedule(
        id = 0,
        assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
    )

    private fun Context.createIntent(withAction: String, apply: (Intent) -> Intent): Intent =
        Intent(this, SchedulerService::class.java).apply {
            action = withAction
            apply(this)
        }
}
