package stasis.test.specs.unit.shared.interop.analytics

import java.time.Instant
import java.util.UUID

import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry

import stasis.shared.api.Formats._
import stasis.shared.api.requests.CreateAnalyticsEntry
import stasis.shared.api.responses.CreatedAnalyticsEntry
import stasis.shared.api.responses.DeletedAnalyticsEntry
import stasis.test.specs.unit.shared.interop.InteropSpec

class CreateAnalyticsEntryInteropSpec extends InteropSpec {
  "CreateAnalyticsEntry interop" should "decode and re-encode a request" in {
    assert(
      domain = "analytics",
      resource = "CreateAnalyticsEntry",
      matches = CreateAnalyticsEntry(
        entry = AnalyticsEntry.Collected(
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
          updated = Instant.parse("2026-04-10T08:18:00Z")
        )
      )
    )
  }

  it should "decode and re-encode a request with a failure stack trace" in {
    assert(
      domain = "analytics",
      resource = "CreateAnalyticsEntry.with-stack-trace",
      matches = CreateAnalyticsEntry(
        entry = AnalyticsEntry.Collected(
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
              timestamp = Instant.parse("2026-04-10T08:17:30Z"),
              stackTrace = Some("at stasis.client.api.Http.connect(Http.scala:42)")
            )
          ),
          created = Instant.parse("2026-04-10T08:15:00Z"),
          updated = Instant.parse("2026-04-10T08:18:00Z")
        )
      )
    )
  }

  it should "decode and re-encode CreatedAnalyticsEntry" in {
    assert(
      domain = "analytics",
      resource = "CreatedAnalyticsEntry",
      matches = CreatedAnalyticsEntry(
        entry = UUID.fromString("f2d2371a-55ea-452b-927b-a26d7e13d95e")
      )
    )
  }

  it should "decode and re-encode DeletedAnalyticsEntry" in {
    assert(
      domain = "analytics",
      resource = "DeletedAnalyticsEntry",
      matches = DeletedAnalyticsEntry(existing = true)
    )
  }
}
