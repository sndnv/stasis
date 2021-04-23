package stasis.test.client_android.lib.model.server.schedules

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import stasis.client_android.lib.model.server.schedules.Schedule
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

class ScheduleSpec : WordSpec({
    "A Schedule" should {
        "calculate the next invocation date/time" {
            val now = LocalDateTime.now()

            val interval = Duration.ofSeconds(10)
            val startOffset = 2.5

            val pastSchedule = Schedule(
                id = UUID.randomUUID(),
                info = "test-schedule",
                isPublic = true,
                start = now.minusSeconds((interval.multipliedBy(startOffset.toLong())).seconds),
                interval = interval
            )

            val recentSchedule = pastSchedule.copy(start = now)

            val futureSchedule = pastSchedule.copy(start = now.plusSeconds((interval.multipliedBy(startOffset.toLong())).seconds))

            pastSchedule.nextInvocation() shouldBe (pastSchedule.start.plusSeconds((interval.multipliedBy((startOffset + 1).toLong())).seconds))

            recentSchedule.nextInvocation() shouldBe (recentSchedule.start.plusSeconds(recentSchedule.interval.seconds))

            futureSchedule.nextInvocation() shouldBe (futureSchedule.start)
        }
    }
})
