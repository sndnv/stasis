package stasis.test.client_android.lib.interop.schedules

import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.test.client_android.lib.interop.InteropSpec
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class ScheduleInteropSpec : InteropSpec({
    "Schedule interop" should {
        "decode and re-encode a schedule" {
            assert(
                domain = "schedules",
                resource = "Schedule",
                matches = Schedule(
                    id = UUID.fromString("e4e1f2a7-ff2d-4e8f-b715-9bbe607684d4"),
                    info = "nightly backup",
                    isPublic = true,
                    start = LocalDateTime.parse("2026-05-01T02:00:30"),
                    interval = Duration.ofSeconds(86400),
                    created = Instant.parse("2026-05-01T09:00:00Z"),
                    updated = Instant.parse("2026-05-02T09:00:00Z")
                )
            )
        }
    }
})
