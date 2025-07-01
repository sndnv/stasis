package stasis.test.specs.unit.shared.api.requests

import java.time.Instant

import io.github.sndnv.layers.telemetry.ApplicationInformation
import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.api.requests.CreateAnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry
import stasis.test.specs.unit.UnitSpec

class CreateAnalyticsEntrySpec extends UnitSpec {
  it should "convert requests to stored entries" in {
    val now = Instant.now()

    val underlyingEntry = AnalyticsEntry.collected(app = ApplicationInformation.none)

    val expectedEntry = StoredAnalyticsEntry(
      id = StoredAnalyticsEntry.generateId(),
      runtime = underlyingEntry.runtime,
      events = underlyingEntry.events,
      failures = underlyingEntry.failures,
      created = underlyingEntry.created,
      updated = underlyingEntry.updated,
      received = now
    )

    val request = CreateAnalyticsEntry(entry = underlyingEntry)

    request.toStoredAnalyticsEntry.copy(
      id = expectedEntry.id,
      created = underlyingEntry.created,
      updated = underlyingEntry.updated,
      received = expectedEntry.received
    ) should be(expectedEntry)
  }
}
