package stasis.shared.api.responses

import stasis.shared.model.analytics.StoredAnalyticsEntry

final case class CreatedAnalyticsEntry(entry: StoredAnalyticsEntry.Id)
