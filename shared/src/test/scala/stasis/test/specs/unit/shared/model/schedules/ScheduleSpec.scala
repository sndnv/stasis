package stasis.test.specs.unit.shared.model.schedules

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import scala.concurrent.duration._

import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

class ScheduleSpec extends UnitSpec {
  "A Schedule" should "calculate the next invocation date/time" in withRetry {
    val now = LocalDateTime.now()

    val interval = 10.seconds
    val startOffset = 2.5

    val pastSchedule = Schedule(
      id = Schedule.generateId(),
      info = "test-schedule",
      isPublic = true,
      start = now.minusSeconds((interval * startOffset).toSeconds),
      interval = interval
    )

    val recentSchedule = pastSchedule.copy(start = now)

    val futureSchedule = pastSchedule.copy(start = now.plusSeconds((interval * startOffset).toSeconds))

    pastSchedule.nextInvocation should be(
      pastSchedule.start.plusSeconds((interval * (startOffset + 1).toLong).toSeconds)
    )

    recentSchedule.nextInvocation should be(
      recentSchedule.start.plusSeconds(recentSchedule.interval.toSeconds)
    )

    futureSchedule.nextInvocation should be(
      futureSchedule.start
    )
  }

  it should "enforce a minimum interval of one millisecond when calculating next invocation" in withRetry {
    val schedule = Schedule(
      id = Schedule.generateId(),
      info = "test-schedule",
      isPublic = true,
      start = LocalDateTime.now(),
      interval = 0.millis
    )

    schedule.nextInvocation should be(schedule.start.plus(1, ChronoUnit.MILLIS))
  }
}
