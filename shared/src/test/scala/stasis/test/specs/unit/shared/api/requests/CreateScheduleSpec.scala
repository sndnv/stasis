package stasis.test.specs.unit.shared.api.requests

import java.time.LocalTime

import scala.concurrent.duration._

import stasis.shared.api.requests.CreateSchedule
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.UnitSpec

class CreateScheduleSpec extends UnitSpec {
  it should "convert requests to schedules" in {
    val expectedSchedule = Schedule(
      id = Schedule.generateId(),
      process = Schedule.Process.Backup,
      instant = LocalTime.now(),
      interval = 3.seconds,
      missed = Schedule.MissedAction.ExecuteImmediately,
      overlap = Schedule.OverlapAction.ExecuteAnyway
    )

    val request = CreateSchedule(
      process = expectedSchedule.process,
      instant = expectedSchedule.instant,
      interval = expectedSchedule.interval,
      missed = expectedSchedule.missed,
      overlap = expectedSchedule.overlap
    )

    request.toSchedule.copy(id = expectedSchedule.id) should be(expectedSchedule)
  }
}
