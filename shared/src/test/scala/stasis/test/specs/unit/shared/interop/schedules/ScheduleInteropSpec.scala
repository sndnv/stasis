package stasis.test.specs.unit.shared.interop.schedules

import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

import scala.concurrent.duration._

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateSchedule
import stasis.shared.api.requests.UpdateSchedule
import stasis.shared.api.responses.CreatedSchedule
import stasis.shared.api.responses.DeletedSchedule
import stasis.shared.model.schedules.Schedule
import stasis.test.specs.unit.shared.interop.InteropSpec

class ScheduleInteropSpec extends InteropSpec {
  "Schedule interop" should "decode and re-encode a schedule" in {
    assert(
      domain = "schedules",
      resource = "Schedule",
      matches = Schedule(
        id = UUID.fromString("e4e1f2a7-ff2d-4e8f-b715-9bbe607684d4"),
        info = "nightly backup",
        isPublic = true,
        start = LocalDateTime.parse("2026-05-01T02:00:30"),
        interval = 86400.seconds,
        created = Instant.parse("2026-05-01T09:00:00Z"),
        updated = Instant.parse("2026-05-02T09:00:00Z")
      )
    )
  }

  it should "decode and re-encode CreateSchedule" in {
    assert(
      domain = "schedules",
      resource = "CreateSchedule",
      matches = CreateSchedule(
        info = "nightly backup",
        isPublic = true,
        start = LocalDateTime.parse("2026-05-01T02:00:30"),
        interval = 86400.seconds
      )
    )
  }

  it should "decode and re-encode UpdateSchedule" in {
    assert(
      domain = "schedules",
      resource = "UpdateSchedule",
      matches = UpdateSchedule(
        info = "updated nightly backup",
        start = LocalDateTime.parse("2026-06-01T03:00:30"),
        interval = 43200.seconds
      )
    )
  }

  it should "decode and re-encode CreatedSchedule" in {
    assert(
      domain = "schedules",
      resource = "CreatedSchedule",
      matches = CreatedSchedule(
        schedule = UUID.fromString("b8164e47-5ff7-4f99-b0b4-f72a4cd5b792")
      )
    )
  }

  it should "decode and re-encode DeletedSchedule" in {
    assert(
      domain = "schedules",
      resource = "DeletedSchedule",
      matches = DeletedSchedule(existing = true)
    )
  }
}
