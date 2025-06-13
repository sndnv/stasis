package stasis.client_android.telemetry.analytics

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.telemetry.analytics.AnalyticsClient
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.telemetry.analytics.AnalyticsPersistence
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.persistence.config.ConfigRepository.Companion.getAnalyticsCachedEntry
import stasis.client_android.persistence.config.ConfigRepository.Companion.putAnalyticsCachedEntry
import stasis.client_android.settings.Settings.getAnalyticsKeepEvents
import stasis.client_android.settings.Settings.getAnalyticsKeepFailures
import java.lang.reflect.Type
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class DefaultAnalyticsPersistence(
    private val preferences: SharedPreferences,
    private val client: () -> AnalyticsClient
) : AnalyticsPersistence {
    private val lastCachedRef: AtomicReference<Instant> = AtomicReference(Instant.EPOCH)
    private val lastTransmittedRef: AtomicReference<Instant> = AtomicReference(Instant.EPOCH)

    override fun cache(entry: AnalyticsEntry) {
        preferences.putAnalyticsCachedEntry(entry = serialize(entry))
        lastCachedRef.set(Instant.now())
    }

    override suspend fun transmit(entry: AnalyticsEntry): Try<Unit> {
        val updated = if (preferences.getAnalyticsKeepEvents()) entry else entry.asCollected().discardEvents()
        val outgoing = if (preferences.getAnalyticsKeepFailures()) updated else updated.asCollected().discardFailures()

        return client().sendAnalyticsEntry(entry = outgoing).map {
            lastTransmittedRef.set(Instant.now())
        }
    }

    override suspend fun restore(): Try<AnalyticsEntry?> =
        Try {
            preferences.getAnalyticsCachedEntry()?.let {
                val stored = deserialize(it)

                lastCachedRef.updateAndGet { existing -> stored.lastCached.max(existing) }
                lastTransmittedRef.updateAndGet { existing -> stored.lastTransmitted.max(existing) }

                stored.entry.asCollected()
            }
        }

    override val lastCached: Instant
        get() = lastCachedRef.get()

    override val lastTransmitted: Instant
        get() = lastTransmittedRef.get()

    internal fun serialize(entry: AnalyticsEntry): String =
        serialize(
            entry = StoredAnalyticsEntry(
                entry = entry.asJson(),
                lastCached = lastCachedRef.get(),
                lastTransmitted = lastTransmittedRef.get()
            )
        )

    internal fun serialize(entry: StoredAnalyticsEntry): String =
        gson.toJson(entry)

    internal fun deserialize(entry: String): StoredAnalyticsEntry =
        gson.fromJson(entry, StoredAnalyticsEntry::class.java)

    data class StoredAnalyticsEntry(
        val entry: AnalyticsEntry.AsJson,
        val lastCached: Instant,
        val lastTransmitted: Instant
    )

    companion object {
        operator fun invoke(preferences: SharedPreferences, clients: Clients): DefaultAnalyticsPersistence =
            DefaultAnalyticsPersistence(preferences = preferences, client = { clients.api })

        private val instantConverter = object : JsonSerializer<Instant>, JsonDeserializer<Instant> {
            override fun serialize(
                src: Instant?,
                typeOfSrc: Type?,
                context: JsonSerializationContext?
            ): JsonElement =
                context?.serialize(src?.toEpochMilli()) ?: JsonNull.INSTANCE

            override fun deserialize(
                json: JsonElement?,
                typeOfT: Type?,
                context: JsonDeserializationContext?
            ): Instant =
                json?.asLong?.let { Instant.ofEpochMilli(it) } ?: Instant.EPOCH
        }

        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(Instant::class.java, instantConverter)
            .create()

        fun Instant.min(other: Instant): Instant =
            if (this.isBefore(other)) this
            else other

        fun Instant.max(other: Instant): Instant =
            if (this.isAfter(other)) this
            else other
    }
}
