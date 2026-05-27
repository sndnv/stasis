package stasis.test.client_android.lib.interop.analytics

import stasis.client_android.lib.model.server.api.requests.CreateAnalyticsEntry
import stasis.client_android.lib.model.server.api.responses.CreatedAnalyticsEntry
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.test.client_android.lib.interop.InteropSpec
import java.time.Instant
import java.util.UUID

class CreateAnalyticsEntryInteropSpec : InteropSpec({
    "CreateAnalyticsEntry interop" should {
        "decode and re-encode a request" {
            assert(
                domain = "analytics",
                resource = "CreateAnalyticsEntry",
                matches = CreateAnalyticsEntry(
                    entry = AnalyticsEntry.AsJson(
                        entryType = "collected",
                        runtime = AnalyticsEntry.RuntimeInformation(
                            id = "10136ad4-c1c2-469b-94b9-79af1e2b939e",
                            app = "stasis-client;1.0.0",
                            jre = "none",
                            os = "linux;6.1.0;x86_64"
                        ),
                        events = listOf(AnalyticsEntry.Event(id = 0, event = "backup_started")),
                        failures = listOf(
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

        "decode and re-encode a request with a failure stack trace" {
            assert(
                domain = "analytics",
                resource = "CreateAnalyticsEntry.with-stack-trace",
                matches = CreateAnalyticsEntry(
                    entry = AnalyticsEntry.AsJson(
                        entryType = "collected",
                        runtime = AnalyticsEntry.RuntimeInformation(
                            id = "10136ad4-c1c2-469b-94b9-79af1e2b939e",
                            app = "stasis-client;1.0.0",
                            jre = "none",
                            os = "linux;6.1.0;x86_64"
                        ),
                        events = listOf(AnalyticsEntry.Event(id = 0, event = "backup_started")),
                        failures = listOf(
                            AnalyticsEntry.Failure(
                                message = "connection refused",
                                timestamp = Instant.parse("2026-04-10T08:17:30Z"),
                                stackTrace = "at stasis.client.api.Http.connect(Http.scala:42)"
                            )
                        ),
                        created = Instant.parse("2026-04-10T08:15:00Z"),
                        updated = Instant.parse("2026-04-10T08:18:00Z")
                    )
                )
            )
        }

        "decode and re-encode CreatedAnalyticsEntry" {
            assert(
                domain = "analytics",
                resource = "CreatedAnalyticsEntry",
                matches = CreatedAnalyticsEntry(
                    entry = UUID.fromString("f2d2371a-55ea-452b-927b-a26d7e13d95e")
                )
            )
        }
    }
})
