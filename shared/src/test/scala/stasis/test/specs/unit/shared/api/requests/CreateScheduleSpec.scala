package stasis.test.specs.unit.shared.api.requests

import java.time.Instant
import java.time.LocalDateTime

import scala.concurrent.duration._

import stasis.shared.api.requests.CreateSchedule
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

class CreateScheduleSpec extends UnitSpec {
  it should "convert requests to schedules" in {
    val expectedSchedule = Schedule(
      id = Schedule.generateId(),
      info = "test-info",
      isPublic = true,
      start = LocalDateTime.now(),
      interval = 3.seconds,
      created = Instant.now(),
      updated = Instant.now()
    )

    val request = CreateSchedule(
      info = expectedSchedule.info,
      isPublic = expectedSchedule.isPublic,
      start = expectedSchedule.start,
      interval = expectedSchedule.interval
    )

    request.toSchedule.copy(
      id = expectedSchedule.id,
      created = expectedSchedule.created,
      updated = expectedSchedule.updated
    ) should be(expectedSchedule)
  }
}
