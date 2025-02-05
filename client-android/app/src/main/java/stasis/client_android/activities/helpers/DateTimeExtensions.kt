package stasis.client_android.activities.helpers

import android.content.Context
import stasis.client_android.R
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.settings.Settings
import stasis.client_android.settings.Settings.getDateTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

object DateTimeExtensions {
    fun Instant.formatAsTime(context: Context): String =
        timeOnlyFormatter(context).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsTime(format: Settings.DateTimeFormat): String =
        timeOnlyFormatter(format).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsDate(context: Context): String =
        dateOnlyFormatter(context).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsDate(format: Settings.DateTimeFormat): String =
        dateOnlyFormatter(format).format(this.atZone(ZoneId.systemDefault()))

    fun Instant.formatAsDateTime(context: Context): Pair<String, String> {
        val zoned = this.atZone(ZoneId.systemDefault())

        val today = ZonedDateTime.now()
        val yesterday = today.minusDays(1)
        val tomorrow = today.plusDays(1)

        val date = when {
            sameDay(zoned, yesterday) -> context.getString(R.string.date_time_format_yesterday)
            sameDay(zoned, today) -> context.getString(R.string.date_time_format_today)
            sameDay(zoned, tomorrow) -> context.getString(R.string.date_time_format_tomorrow)
            else -> dateOnlyFormatter(context).format(zoned)
        }

        val time = timeOnlyFormatter(context).format(zoned)

        return date to time
    }

    fun LocalDateTime.formatAsDate(context: Context): String =
        dateOnlyFormatter(context).format(this.atZone(ZoneId.systemDefault()))

    fun LocalDateTime.formatAsTime(context: Context): String =
        timeOnlyFormatter(context).format(this.atZone(ZoneId.systemDefault()))

    fun LocalTime.formatAsTime(context: Context): String =
        timeOnlyFormatter(context).format(this)

    fun Instant.formatAsFullDateTime(context: Context): String =
        dateTimeFormatter(context).format(this.atZone(ZoneId.systemDefault()))

    fun CharSequence.parseAsTime(context: Context): Instant {
        val time = this.parseAsLocalTime(context)
        return time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant()
    }

    fun CharSequence.parseAsLocalTime(context: Context): LocalTime =
        LocalTime.parse(this, timeOnlyFormatter(context))

    fun CharSequence.parseAsDate(context: Context): Instant {
        val date = this.parseAsLocalDate(context)
        return date.atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    fun CharSequence.parseAsLocalDate(context: Context): LocalDate =
        LocalDate.parse(this, dateOnlyFormatter(context))

    fun CharSequence.parseAsFullDateTime(context: Context): Instant =
        LocalDateTime.parse(this, dateTimeFormatter(context)).atZone(ZoneId.systemDefault())
            .toInstant()

    fun Pair<CharSequence, CharSequence>.parseAsDateTime(context: Context): Instant {
        val date = this.first.parseAsLocalDate(context)
        val time = this.second.parseAsLocalTime(context)

        return LocalDateTime.of(date.year, date.month, date.dayOfMonth, time.hour, time.minute)
            .atZone(ZoneId.systemDefault()).toInstant()
    }

    fun Pair<CharSequence, CharSequence>.parseAsLocalDateTime(context: Context): LocalDateTime {
        val date = this.first.parseAsLocalDate(context)
        val time = this.second.parseAsLocalTime(context)

        return LocalDateTime.of(date.year, date.month, date.dayOfMonth, time.hour, time.minute)
    }

    fun Instant.isToday(): Boolean =
        sameDay(this.atZone(ZoneId.systemDefault()), ZonedDateTime.now())

    private const val IsoDateOnlyPattern: String = "yyyy-MM-dd"
    private const val IsoTimeOnlyPattern: String = "HH:mm"
    private const val IsoDateTimePattern: String = "yyyy-MM-dd HH:mm"

    private fun dateOnlyFormatter(context: Context): DateTimeFormatter =
        dateOnlyFormatter(ConfigRepository.getPreferences(context).getDateTimeFormat())

    private fun timeOnlyFormatter(context: Context): DateTimeFormatter =
        timeOnlyFormatter(ConfigRepository.getPreferences(context).getDateTimeFormat())

    private fun dateTimeFormatter(context: Context): DateTimeFormatter =
        dateTimeFormatter(ConfigRepository.getPreferences(context).getDateTimeFormat())

    private fun dateOnlyFormatter(format: Settings.DateTimeFormat): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            when (format) {
                Settings.DateTimeFormat.System -> DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    FormatStyle.MEDIUM,
                    null,
                    IsoChronology.INSTANCE,
                    Locale.getDefault()
                )

                Settings.DateTimeFormat.Iso -> IsoDateOnlyPattern
            }
        )

    private fun timeOnlyFormatter(format: Settings.DateTimeFormat): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            when (format) {
                Settings.DateTimeFormat.System -> DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    null,
                    FormatStyle.SHORT,
                    IsoChronology.INSTANCE,
                    Locale.getDefault()
                )

                Settings.DateTimeFormat.Iso -> IsoTimeOnlyPattern
            }
        )

    private fun dateTimeFormatter(format: Settings.DateTimeFormat): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            when (format) {
                Settings.DateTimeFormat.System -> DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                    FormatStyle.MEDIUM,
                    FormatStyle.SHORT,
                    IsoChronology.INSTANCE,
                    Locale.getDefault()
                )

                Settings.DateTimeFormat.Iso -> IsoDateTimePattern
            }
        )

    private fun sameDay(a: ZonedDateTime, b: ZonedDateTime): Boolean =
        a.year == b.year && a.monthValue == b.monthValue && a.dayOfMonth == b.dayOfMonth
}
