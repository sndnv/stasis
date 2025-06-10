package stasis.client_android.telemetry.analytics

import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.lib.telemetry.ApplicationInformation
import stasis.client_android.lib.telemetry.analytics.AnalyticsEntry
import stasis.client_android.lib.utils.Try
import stasis.client_android.mocks.MockAnalyticsClient
import stasis.client_android.persistence.config.ConfigRepository.Companion.Keys
import stasis.client_android.settings.Settings
import stasis.client_android.telemetry.analytics.DefaultAnalyticsPersistence.Companion.max
import stasis.client_android.telemetry.analytics.DefaultAnalyticsPersistence.Companion.min
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DefaultAnalyticsPersistenceSpec {
    @Test
    fun cacheEntriesLocally() {
        val preferences = mockk<SharedPreferences>()
        val client = MockAnalyticsClient()
        val persistence = DefaultAnalyticsPersistence(preferences) { client }

        val expectedJson = persistence.serialize(entry)

        val editor = mockk<SharedPreferences.Editor>(relaxUnitFun = true)

        every { preferences.edit() } returns editor
        every { editor.putString(Keys.Analytics.EntryCache, expectedJson) } returns editor
        every { editor.commit() } returns true

        assertThat(persistence.lastCached, equalTo(Instant.EPOCH))

        persistence.cache(entry)

        assertThat(persistence.lastCached > Instant.EPOCH, equalTo(true))
    }

    @Test
    fun transmitEntriesRemotelyWithEventsAndFailures() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepEvents,
                Settings.Defaults.AnalyticsKeepEvents
            )
        } returns true
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepFailures,
                Settings.Defaults.AnalyticsKeepFailures
            )
        } returns true

        val client = MockAnalyticsClient()
        val persistence = DefaultAnalyticsPersistence(preferences) { client }

        assertThat(client.sent, equalTo(0))
        assertThat(client.lastEntry, equalTo(null))
        assertThat(persistence.lastTransmitted, equalTo(Instant.EPOCH))

        runBlocking { persistence.transmit(entry) }

        assertThat(client.sent, equalTo(1))
        assertThat(client.lastEntry, equalTo(entry))
        assertThat(persistence.lastTransmitted > Instant.EPOCH, equalTo(true))
    }

    @Test
    fun transmitEntriesRemotelyWithoutEventsAndFailures() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepEvents,
                Settings.Defaults.AnalyticsKeepEvents
            )
        } returns false
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepFailures,
                Settings.Defaults.AnalyticsKeepFailures
            )
        } returns false

        val client = MockAnalyticsClient()
        val persistence = DefaultAnalyticsPersistence(preferences) { client }

        assertThat(client.sent, equalTo(0))
        assertThat(client.lastEntry, equalTo(null))
        assertThat(persistence.lastTransmitted, equalTo(Instant.EPOCH))

        runBlocking { persistence.transmit(entry) }

        val now = Instant.now()

        assertThat(client.sent, equalTo(1))
        assertThat(
            client.lastEntry?.asCollected()?.copy(updated = now),
            equalTo(entry.copy(events = emptyList(), failures = emptyList(), updated = now))
        )
        assertThat(persistence.lastTransmitted > Instant.EPOCH, equalTo(true))
    }

    @Test
    fun restoreEntriesFromLocalCacheWhenAvailable() {
        val preferences = mockk<SharedPreferences>()
        val client = MockAnalyticsClient()
        val persistence = DefaultAnalyticsPersistence(preferences) { client }

        val now = Instant.now()

        val expectedJson = persistence.serialize(
            DefaultAnalyticsPersistence.StoredAnalyticsEntry(
                entry = entry.asJson(),
                lastCached = now,
                lastTransmitted = now
            )
        )

        every { preferences.getString(Keys.Analytics.EntryCache, null) } returns expectedJson

        runBlocking {
            assertThat(persistence.restore(), equalTo(Try.Success(entry)))
        }
    }

    @Test
    fun notRestoreEntriesFromLocalCacheWhenNotAvailable() {
        val preferences = mockk<SharedPreferences>()
        val client = MockAnalyticsClient()
        val persistence = DefaultAnalyticsPersistence(preferences) { client }

        every { preferences.getString(Keys.Analytics.EntryCache, null) } returns null

        runBlocking {
            assertThat(persistence.restore(), equalTo(Try.Success(null)))
        }
    }

    @Test
    fun compareInstants() {
        val preferences = mockk<SharedPreferences>()
        val client = MockAnalyticsClient()
        val persistence = DefaultAnalyticsPersistence(preferences) { client }

        val now = Instant.now()
        val before = now.minusSeconds(1)
        val later = now.plusSeconds(1)

        assertThat(now.min(before), equalTo(before))
        assertThat(now.min(later), equalTo(now))
        assertThat(now.min(now), equalTo(now))

        assertThat(now.max(before), equalTo(now))
        assertThat(now.max(later), equalTo(later))
        assertThat(now.max(now), equalTo(now))
    }

    private val app = ApplicationInformation.none()
    private val entry = AnalyticsEntry.collected(app)
        .withEvent(name = "test", emptyMap())
        .withFailure(message = "Test failure")
}
