package stasis.test.specs.unit.shared.interop.analytics

import java.time.Instant
import java.util.UUID

import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry

import stasis.shared.api.Formats._
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.test.specs.unit.shared.interop.InteropSpec

class StoredAnalyticsEntryInteropSpec extends InteropSpec {
  "StoredAnalyticsEntry interop" should "decode and re-encode a stored entry" in {
    assert(
      domain = "analytics",
      resource = "StoredAnalyticsEntry",
      matches = StoredAnalyticsEntry(
        id = UUID.fromString("ac676261-a13e-44ce-bf0d-ac20147ce742"),
        runtime = AnalyticsEntry.RuntimeInformation(
          id = "10136ad4-c1c2-469b-94b9-79af1e2b939e",
          app = "stasis-client;1.0.0",
          jre = "none",
          os = "linux;6.1.0;x86_64"
        ),
        events = Seq(AnalyticsEntry.Event(id = 0, event = "backup_started")),
        failures = Seq(
          AnalyticsEntry.Failure(
            message = "connection refused",
            timestamp = Instant.parse("2026-04-10T08:17:30Z")
          )
        ),
        created = Instant.parse("2026-04-10T08:15:00Z"),
        updated = Instant.parse("2026-04-10T08:18:00Z"),
        received = Instant.parse("2026-04-10T08:18:05Z")
      )
    )
  }
}
