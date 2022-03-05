package stasis.client_android.settings

import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.Duration
import java.util.Calendar

object Settings {
    fun SharedPreferences.getDateTimeFormat(): DateTimeFormat {
        return parseDateTimeFormat(
            getString(Keys.DateTimeFormat, Defaults.DateTimeFormat) ?: Defaults.DateTimeFormat
        )
    }

    fun SharedPreferences.getSchedulingEnabled(): Boolean {
        return getBoolean(Keys.SchedulingEnabled, Defaults.SchedulingEnabled)
    }

    fun SharedPreferences.getPingInterval(): Duration {
        return getString(Keys.PingInterval, null)?.toLongOrNull()?.let { Duration.ofSeconds(it) }
            ?: Defaults.PingInterval
    }

    fun parseDateTimeFormat(format: String): DateTimeFormat =
        when (format) {
            "system" -> DateTimeFormat.System
            "iso" -> DateTimeFormat.Iso
            else -> throw IllegalArgumentException("Unexpected format found: [$format]")
        }

    fun Int.toDayOfWeek(): DayOfWeek = when (this) {
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        Calendar.SATURDAY -> DayOfWeek.SATURDAY
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        else -> throw IllegalArgumentException("Unexpected day of the week found: [$this]")
    }

    fun DayOfWeek.toCalendarDay(): Int = when (this) {
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
    }

    object Keys {
        const val DateTimeFormat: String = "date_time_format"
        const val ManageDeviceSecret: String = "manage_device_secret"
        const val SchedulingEnabled: String = "scheduling_enabled"
        const val PingInterval: String = "ping_interval"
        const val ResetConfig: String = "reset_config"
    }

    object Defaults {
        const val DateTimeFormat: String = "system"
        const val SchedulingEnabled: Boolean = true
        val PingInterval: Duration = Duration.ofMinutes(3)
    }

    sealed class DateTimeFormat {
        object System : DateTimeFormat()
        object Iso : DateTimeFormat()
    }
}
