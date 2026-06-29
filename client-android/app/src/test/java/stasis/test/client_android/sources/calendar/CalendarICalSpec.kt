package stasis.test.client_android.sources.calendar

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import stasis.client_android.sources.calendar.CalendarAttendee
import stasis.client_android.sources.calendar.CalendarEvent
import stasis.client_android.sources.calendar.CalendarICal
import stasis.client_android.sources.calendar.CalendarReminder

class CalendarICalSpec {
    @Test
    fun renderAnEventAsICalendar() {
        val text = unfold(CalendarICal.toICal(event()))

        assertThat(text, containsString("BEGIN:VCALENDAR"))
        assertThat(text, containsString("VERSION:2.0"))
        assertThat(text, containsString("PRODID:-//stasis//stasis Android//EN"))
        assertThat(text, containsString("BEGIN:VEVENT"))
        assertThat(text, containsString("UID:16f7f6f2-6e2a-4a29-9d2e-2a6f5f0e9b11"))
        assertThat(text, containsString("SUMMARY:test"))
        assertThat(text, containsString("DTSTART:"))
        assertThat(text, containsString("DTEND:"))
        assertThat(text, containsString("DESCRIPTION:test a"))
        assertThat(text, containsString("LOCATION:test loc"))
        assertThat(text, containsString("RRULE:FREQ=DAILY"))
        assertThat(text, containsString("ORGANIZER"))
        assertThat(text, containsString("host@example.com"))
        assertThat(text, containsString("ATTENDEE"))
        assertThat(text, containsString("guest@example.com"))
        assertThat(text, containsString("BEGIN:VALARM"))
        assertThat(text, containsString("ACTION:DISPLAY"))
        assertThat(text, containsString("TRIGGER:-PT10M"))
        assertThat(text, containsString("END:VEVENT"))
        assertThat(text, containsString("END:VCALENDAR"))
    }

    @Test
    fun renderAnAllDayEventAsADate() {
        val text = unfold(CalendarICal.toICal(event().copy(allDay = true, end = null, duration = null)))

        assertThat(text, containsString("DTSTART;VALUE=DATE:"))
    }

    private fun event(): CalendarEvent = CalendarEvent(
        id = "16f7f6f2-6e2a-4a29-9d2e-2a6f5f0e9b11",
        calendar = "test a",
        title = "test",
        description = "test a",
        location = "test loc",
        start = 1_700_000_000_000L,
        end = 1_700_000_000_000L + 3_600_000L,
        duration = null,
        allDay = false,
        timezone = null,
        endTimezone = null,
        rrule = "FREQ=DAILY",
        rdate = null,
        exdate = null,
        organizer = "host@example.com",
        availability = 0,
        accessLevel = 0,
        status = 0,
        hasAlarm = true,
        reminders = listOf(CalendarReminder(minutes = 10, method = 1)),
        attendees = listOf(CalendarAttendee(name = "Guest", email = "guest@example.com", relationship = 0, type = 0, status = 0))
    )

    private fun unfold(bytes: ByteArray): String = bytes.decodeToString().replace("\r\n ", "").replace("\n ", "")
}
