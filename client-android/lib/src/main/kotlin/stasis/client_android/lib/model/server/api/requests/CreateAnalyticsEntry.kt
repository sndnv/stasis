package stasis.client_android.lib.model.server.api.requests

import com.squareup.moshi.JsonClass
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry

@JsonClass(generateAdapter = true)
data class CreateAnalyticsEntry(
    val entry: AnalyticsEntry.AsJson
)
