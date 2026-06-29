package stasis.client_android.sources.calendar

import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.ComponentList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.PropertyList
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStamp
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Location
import net.fortuna.ical4j.model.property.Organizer
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import net.fortuna.ical4j.model.property.immutable.ImmutableAction
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object CalendarICal {
    fun toICal(event: CalendarEvent): ByteArray {
        val calendar = Calendar(
            PropertyList(listOf(ProdId(ProductId), ImmutableVersion.VERSION_2_0, ImmutableCalScale.GREGORIAN)),
            ComponentList(listOf(vevent(event)))
        )

        return ByteArrayOutputStream().use { out ->
            CalendarOutputter(false).output(calendar, out)
            out.toByteArray()
        }
    }

    private fun vevent(event: CalendarEvent): VEvent {
        val properties = mutableListOf<Property>(
            Uid(event.id),
            DtStamp(),
            Summary(event.title),
            dtStart(event)
        )

        endOrDuration(event)?.let { properties.add(it) }
        event.description?.takeIf { it.isNotBlank() }?.let { properties.add(Description(it)) }
        event.location?.takeIf { it.isNotBlank() }?.let { properties.add(Location(it)) }
        event.rrule?.takeIf { it.isNotBlank() }?.let { properties.add(RRule<Instant>(it)) }
        organizer(event)?.let { properties.add(it) }
        event.attendees.forEach { attendee -> this.attendee(attendee.email)?.let { properties.add(it) } }

        return VEvent(PropertyList(properties), ComponentList(alarms(event)))
    }

    private fun dtStart(event: CalendarEvent): Property =
        if (event.allDay) DtStart(date(event.start)) else DtStart(Instant.ofEpochMilli(event.start))

    private fun endOrDuration(event: CalendarEvent): Property? = when {
        event.end != null ->
            if (event.allDay) DtEnd(date(event.end)) else DtEnd(Instant.ofEpochMilli(event.end))

        event.duration != null && event.duration.isNotBlank() -> Duration(event.duration)
        else -> null
    }

    private fun organizer(event: CalendarEvent): Organizer? =
        event.organizer?.takeIf { it.isNotBlank() }?.let { mailto(it) }?.let { runCatching { Organizer(it) }.getOrNull() }

    private fun attendee(email: String?): Attendee? =
        email?.takeIf { it.isNotBlank() }?.let { mailto(it) }?.let { runCatching { Attendee(it) }.getOrNull() }

    private fun alarms(event: CalendarEvent): List<VAlarm> =
        event.reminders.map { reminder ->
            VAlarm(
                PropertyList(
                    listOf(
                        ImmutableAction.DISPLAY,
                        Trigger(java.time.Duration.ofMinutes(-reminder.minutes.toLong())),
                        Description(event.title.ifBlank { DefaultReminder })
                    )
                )
            )
        }

    private fun mailto(value: String): URI? =
        runCatching { if (value.startsWith("mailto:")) URI(value) else URI("mailto:$value") }.getOrNull()

    private fun date(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    private const val ProductId: String = "-//stasis//stasis Android//EN"

    private const val DefaultReminder: String = "Reminder"
}
