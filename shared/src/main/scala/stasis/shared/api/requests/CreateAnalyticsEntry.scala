package stasis.shared.api.requests

import java.time.Instant

import io.github.sndnv.layers.telemetry.analytics.AnalyticsEntry
import stasis.shared.model.analytics.StoredAnalyticsEntry

final case class CreateAnalyticsEntry(entry: AnalyticsEntry) {
  def toStoredAnalyticsEntry: StoredAnalyticsEntry =
    StoredAnalyticsEntry(
      id = StoredAnalyticsEntry.generateId(),
      runtime = entry.runtime,
      events = entry.events,
      failures = entry.failures,
      created = entry.created,
      updated = entry.updated,
      received = Instant.now()
    )
}
