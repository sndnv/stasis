package stasis.test.specs.unit.shared.api.requests

import java.time.Instant
import java.time.LocalDateTime

import scala.concurrent.duration._

import stasis.shared.api.requests.UpdateSchedule
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

class UpdateScheduleSpec extends UnitSpec {
  it should "convert requests to updated schedules" in {
    val initialSchedule = Schedule(
      id = Schedule.generateId(),
      info = "test-info",
      isPublic = false,
      start = LocalDateTime.now(),
      interval = 3.seconds,
      created = Instant.now(),
      updated = Instant.now()
    )

    val expectedSchedule = initialSchedule.copy(interval = 10.minutes)

    val request = UpdateSchedule(
      info = expectedSchedule.info,
      start = expectedSchedule.start,
      interval = expectedSchedule.interval
    )

    request
      .toUpdatedSchedule(initialSchedule)
      .copy(created = expectedSchedule.created, updated = expectedSchedule.updated) should be(expectedSchedule)
  }
}
