package stasis.test.specs.unit.shared.model.analytics

import java.time.Instant

import io.github.sndnv.layers.testing.UnitSpec
import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry

class StoredAnalyticsEntrySpec extends UnitSpec {
  "A StoredAnalyticsEntry" should "support converting to/from flattened parameters" in {
    val now = Instant.now()

    val original = StoredAnalyticsEntry(
      id = StoredAnalyticsEntry.generateId(),
      runtime = AnalyticsEntry.RuntimeInformation(app = ApplicationInformation.none),
      events = Seq(AnalyticsEntry.Event(id = 0, event = "test-event")),
      failures = Seq(AnalyticsEntry.Failure(message = "test-failure", timestamp = now)),
      created = now.plusMillis(1),
      updated = now.plusSeconds(2),
      received = now.minusSeconds(3)
    )

    val flattened = StoredAnalyticsEntry.flattened(original) match {
      case Some(value) => value
      case None        => fail("Expected a value but none was found")
    }

    val actual = (StoredAnalyticsEntry.fromFlattened _).tupled.apply(flattened)

    actual should be(original)
  }
}
