package stasis.test.specs.unit.server.api.requests

import java.time.LocalTime

import stasis.server.api.requests.UpdateSchedule
import stasis.server.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

import scala.concurrent.duration._

class UpdateScheduleSpec extends UnitSpec {
  it should "convert requests to updated schedules" in {
    val initialSchedule = Schedule(
      id = Schedule.generateId(),
      process = Schedule.Process.Backup,
      instant = LocalTime.now(),
      interval = 3.seconds,
      missed = Schedule.MissedAction.ExecuteImmediately,
      overlap = Schedule.OverlapAction.ExecuteAnyway
    )

    val expectedSchedule = initialSchedule.copy(interval = 10.minutes)

    val request = UpdateSchedule(
      process = expectedSchedule.process,
      instant = expectedSchedule.instant,
      interval = expectedSchedule.interval,
      missed = expectedSchedule.missed,
      overlap = expectedSchedule.overlap
    )

    request.toUpdatedSchedule(initialSchedule) should be(expectedSchedule)
  }
}
