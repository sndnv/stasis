package stasis.client_android.lib.telemetry.analytics

import stasis.client_android.lib.utils.Try

interface AnalyticsClient {
    suspend fun sendAnalyticsEntry(entry: AnalyticsEntry): Try<Unit>
}
