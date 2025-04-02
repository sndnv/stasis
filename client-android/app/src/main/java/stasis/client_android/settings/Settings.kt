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

    fun SharedPreferences.getRestrictionsIgnored(): Boolean {
        return getBoolean(Keys.RestrictionsIgnored, Defaults.RestrictionsIgnored)
    }

    fun SharedPreferences.getSchedulingEnabled(): Boolean {
        return getBoolean(Keys.SchedulingEnabled, Defaults.SchedulingEnabled)
    }

    fun SharedPreferences.getPingInterval(): Duration {
        return getString(Keys.PingInterval, null)?.toLongOrNull()?.let { Duration.ofSeconds(it) }
            ?: Defaults.PingInterval
    }

    fun SharedPreferences.getCommandRefreshInterval(): Duration {
        return getString(Keys.CommandRefreshInterval, null)?.toLongOrNull()?.let { Duration.ofSeconds(it) }
            ?: Defaults.CommandRefreshInterval
    }

    fun SharedPreferences.getDiscoveryInterval(): Duration {
        return getString(Keys.DiscoveryInterval, null)?.toLongOrNull()?.let { Duration.ofSeconds(it) }
            ?: Defaults.DiscoveryInterval
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
        const val ManageUserCredentialsUpdatePassword: String = "manage_user_credentials_update_password"
        const val ManageUserCredentialsUpdateSalt: String = "manage_user_credentials_update_salt"
        const val ManageDeviceSecretRemotelyPush: String = "manage_device_secret_remotely_push"
        const val ManageDeviceSecretRemotelyPull: String = "manage_device_secret_remotely_pull"
        const val ManageDeviceSecretLocallyExport: String = "manage_device_secret_locally_export"
        const val ManageDeviceSecretLocallyImport: String = "manage_device_secret_locally_import"
        const val RestrictionsIgnored: String = "restrictions_ignored"
        const val SchedulingEnabled: String = "scheduling_enabled"
        const val PingInterval: String = "ping_interval"
        const val CommandRefreshInterval: String = "command_refresh_interval"
        const val DiscoveryInterval: String = "discovery_interval"
        const val ShowCommands: String = "show_commands"
        const val ResetConfig: String = "reset_config"
    }

    object Defaults {
        const val DateTimeFormat: String = "system"
        const val RestrictionsIgnored: Boolean = false
        const val SchedulingEnabled: Boolean = true
        val PingInterval: Duration = Duration.ofMinutes(3)
        val CommandRefreshInterval: Duration = Duration.ofMinutes(5)
        val DiscoveryInterval: Duration = Duration.ofMinutes(30)
    }

    sealed class DateTimeFormat {
        data object System : DateTimeFormat()
        data object Iso : DateTimeFormat()
    }
}
