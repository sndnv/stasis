package stasis.client_android.lib.telemetry.analytics

import stasis.client_android.lib.utils.Try
import java.time.Instant

interface AnalyticsPersistence {
    fun cache(entry: AnalyticsEntry)
    suspend fun transmit(entry: AnalyticsEntry): Try<Unit>
    suspend fun restore(): Try<AnalyticsEntry?>

    val lastCached: Instant
    val lastTransmitted: Instant
}
