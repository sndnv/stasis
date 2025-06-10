package stasis.test.client_android.settings

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getAnalyticsEnabled
import stasis.client_android.settings.Settings.getAnalyticsKeepEvents
import stasis.client_android.settings.Settings.getAnalyticsKeepFailures
import stasis.client_android.settings.Settings.getAnalyticsPersistenceInterval
import stasis.client_android.settings.Settings.getAnalyticsTransmissionInterval
import stasis.client_android.settings.Settings.getCommandRefreshInterval
import stasis.client_android.settings.Settings.getDateTimeFormat
import stasis.client_android.settings.Settings.getDiscoveryInterval
import stasis.client_android.settings.Settings.getPingInterval
import stasis.client_android.settings.Settings.getRestrictionsIgnored
import stasis.client_android.settings.Settings.getSchedulingEnabled
import stasis.client_android.settings.Settings.toCalendarDay
import stasis.client_android.settings.Settings.toDayOfWeek
import java.time.DayOfWeek
import java.time.Duration
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SettingsSpec {
    @Test
    fun supportParsingDateTimeFormats() {
        assertThat(
            Settings.parseDateTimeFormat(format = "system"),
            equalTo((Settings.DateTimeFormat.System))
        )
        assertThat(
            Settings.parseDateTimeFormat(format = "iso"),
            equalTo((Settings.DateTimeFormat.Iso))
        )

        try {
            Settings.parseDateTimeFormat(format = "other")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected format found: [other]"))
        }
    }

    @Test
    fun supportConvertingCalendarDaysToDayOfWeek() {
        assertThat(Calendar.MONDAY.toDayOfWeek(), equalTo(DayOfWeek.MONDAY))
        assertThat(Calendar.TUESDAY.toDayOfWeek(), equalTo(DayOfWeek.TUESDAY))
        assertThat(Calendar.WEDNESDAY.toDayOfWeek(), equalTo(DayOfWeek.WEDNESDAY))
        assertThat(Calendar.THURSDAY.toDayOfWeek(), equalTo(DayOfWeek.THURSDAY))
        assertThat(Calendar.FRIDAY.toDayOfWeek(), equalTo(DayOfWeek.FRIDAY))
        assertThat(Calendar.SATURDAY.toDayOfWeek(), equalTo(DayOfWeek.SATURDAY))
        assertThat(Calendar.SUNDAY.toDayOfWeek(), equalTo(DayOfWeek.SUNDAY))

        try {
            42.toDayOfWeek()
            Assert.fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Unexpected day of the week found: [42]"))
        }
    }

    @Test
    fun supportConvertingDayOfWeekToCalendarDays() {
        assertThat(DayOfWeek.MONDAY.toCalendarDay(), equalTo(Calendar.MONDAY))
        assertThat(DayOfWeek.TUESDAY.toCalendarDay(), equalTo(Calendar.TUESDAY))
        assertThat(DayOfWeek.WEDNESDAY.toCalendarDay(), equalTo(Calendar.WEDNESDAY))
        assertThat(DayOfWeek.THURSDAY.toCalendarDay(), equalTo(Calendar.THURSDAY))
        assertThat(DayOfWeek.FRIDAY.toCalendarDay(), equalTo(Calendar.FRIDAY))
        assertThat(DayOfWeek.SATURDAY.toCalendarDay(), equalTo(Calendar.SATURDAY))
        assertThat(DayOfWeek.SUNDAY.toCalendarDay(), equalTo(Calendar.SUNDAY))
    }

    @Test
    fun supportRetrievingDefaultDateTimeFormat() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.DateTimeFormat,
                Settings.Defaults.DateTimeFormat
            )
        } returns null

        val expectedFormat = Settings.Defaults.DateTimeFormat
        assertThat(
            preferences.getDateTimeFormat(),
            equalTo((Settings.parseDateTimeFormat(expectedFormat)))
        )
    }

    @Test
    fun supportRetrievingUserDefinedDateTimeFormat() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.DateTimeFormat,
                Settings.Defaults.DateTimeFormat
            )
        } returns "iso"

        val expectedFormat = "iso"
        assertThat(
            preferences.getDateTimeFormat(),
            equalTo((Settings.parseDateTimeFormat(expectedFormat)))
        )
    }

    @Test
    fun supportRetrievingDefaultRestrictionsIgnoredState() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.RestrictionsIgnored,
                Settings.Defaults.RestrictionsIgnored
            )
        } returns false

        val expectedState = Settings.Defaults.RestrictionsIgnored
        assertThat(preferences.getRestrictionsIgnored(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedRestrictionsIgnoredState() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.RestrictionsIgnored,
                Settings.Defaults.RestrictionsIgnored
            )
        } returns false

        val expectedState = false
        assertThat(preferences.getRestrictionsIgnored(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultSchedulingEnabledState() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.SchedulingEnabled,
                Settings.Defaults.SchedulingEnabled
            )
        } returns true

        val expectedState = Settings.Defaults.SchedulingEnabled
        assertThat(preferences.getSchedulingEnabled(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedSchedulingEnabledState() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.SchedulingEnabled,
                Settings.Defaults.SchedulingEnabled
            )
        } returns false

        val expectedState = false
        assertThat(preferences.getSchedulingEnabled(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultPingInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.PingInterval,
                null
            )
        } returns "180"

        val expectedState = Settings.Defaults.PingInterval
        assertThat(preferences.getPingInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultCommandRefreshInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.CommandRefreshInterval,
                null
            )
        } returns "300"

        val expectedState = Settings.Defaults.CommandRefreshInterval
        assertThat(preferences.getCommandRefreshInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultDiscoveryInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.DiscoveryInterval,
                null
            )
        } returns "1800"

        val expectedState = Settings.Defaults.DiscoveryInterval
        assertThat(preferences.getDiscoveryInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedPingInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.PingInterval,
                null
            )
        } returns "10"

        val expectedState = Duration.ofSeconds(10)
        assertThat(preferences.getPingInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedCommandRefreshInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.CommandRefreshInterval,
                null
            )
        } returns "10"

        val expectedState = Duration.ofSeconds(10)
        assertThat(preferences.getCommandRefreshInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedDiscoveryInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.DiscoveryInterval,
                null
            )
        } returns "1234"

        val expectedState = Duration.ofSeconds(1234)
        assertThat(preferences.getDiscoveryInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultAnalyticsEnabled() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsEnabled,
                Settings.Defaults.AnalyticsEnabled
            )
        } returns Settings.Defaults.AnalyticsEnabled

        val expectedState = Settings.Defaults.AnalyticsEnabled
        assertThat(preferences.getAnalyticsEnabled(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedAnalyticsEnabled() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsEnabled,
                Settings.Defaults.AnalyticsEnabled
            )
        } returns false

        assertThat(preferences.getAnalyticsEnabled(), equalTo(false))
    }

    @Test
    fun supportRetrievingDefaultAnalyticsKeepEvents() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepEvents,
                Settings.Defaults.AnalyticsKeepEvents
            )
        } returns Settings.Defaults.AnalyticsKeepEvents

        val expectedState = Settings.Defaults.AnalyticsKeepEvents
        assertThat(preferences.getAnalyticsKeepEvents(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedAnalyticsKeepEvents() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepEvents,
                Settings.Defaults.AnalyticsKeepEvents
            )
        } returns false

        assertThat(preferences.getAnalyticsKeepEvents(), equalTo(false))
    }

    @Test
    fun supportRetrievingDefaultAnalyticsKeepFailures() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepFailures,
                Settings.Defaults.AnalyticsKeepFailures
            )
        } returns Settings.Defaults.AnalyticsKeepFailures

        val expectedState = Settings.Defaults.AnalyticsKeepFailures
        assertThat(preferences.getAnalyticsKeepFailures(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedAnalyticsKeepFailures() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getBoolean(
                Settings.Keys.AnalyticsKeepFailures,
                Settings.Defaults.AnalyticsKeepFailures
            )
        } returns false

        assertThat(preferences.getAnalyticsKeepFailures(), equalTo(false))
    }

    @Test
    fun supportRetrievingDefaultAnalyticsPersistenceInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.AnalyticsPersistenceInterval,
                null
            )
        } returns "300"

        val expectedState = Settings.Defaults.AnalyticsPersistenceInterval
        assertThat(preferences.getAnalyticsPersistenceInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedAnalyticsPersistenceInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.AnalyticsPersistenceInterval,
                null
            )
        } returns "1234"

        val expectedState = Duration.ofSeconds(1234)
        assertThat(preferences.getAnalyticsPersistenceInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingDefaultAnalyticsTransmissionInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.AnalyticsTransmissionInterval,
                null
            )
        } returns "1800"

        val expectedState = Settings.Defaults.AnalyticsTransmissionInterval
        assertThat(preferences.getAnalyticsTransmissionInterval(), equalTo(expectedState))
    }

    @Test
    fun supportRetrievingUserDefinedAnalyticsTransmissionInterval() {
        val preferences = mockk<SharedPreferences>()
        every {
            preferences.getString(
                Settings.Keys.AnalyticsTransmissionInterval,
                null
            )
        } returns "1234"

        val expectedState = Duration.ofSeconds(1234)
        assertThat(preferences.getAnalyticsTransmissionInterval(), equalTo(expectedState))
    }
}
