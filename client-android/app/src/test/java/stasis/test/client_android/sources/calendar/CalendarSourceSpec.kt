package stasis.test.client_android.sources.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.sources.calendar.CalendarAttendee
import stasis.client_android.sources.calendar.CalendarEvent
import stasis.client_android.sources.calendar.CalendarReminder
import stasis.client_android.sources.calendar.CalendarSource

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class CalendarSourceSpec {
    private fun sourceWith(resolver: ContentResolver): CalendarSource =
        CalendarSource(resolver = resolver, hasReadPermission = { true }, hasWritePermission = { true })

    @Test
    fun listEventsWithRemindersAndAttendees() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(CalendarContract.Events.CONTENT_URI, any(), any(), any(), any()) } returns eventsCursor()
        every { resolver.query(CalendarContract.Reminders.CONTENT_URI, any(), any(), any(), any()) } answers { remindersCursor() }
        every { resolver.query(CalendarContract.Attendees.CONTENT_URI, any(), any(), any(), any()) } answers { attendeesCursor() }

        val events = sourceWith(resolver).list()

        assertThat(events.size, equalTo(1))
        val event = events.single()
        assertThat(event.id, equalTo("uid-1"))
        assertThat(event.title, equalTo("test"))
        assertThat(event.start, equalTo(1000L))
        assertThat(event.end, equalTo(2000L))
        assertThat(event.allDay, equalTo(false))
        assertThat(event.hasAlarm, equalTo(true))
        assertThat(event.reminders, equalTo(listOf(CalendarReminder(minutes = 10, method = 1))))
        assertThat(event.attendees.single().email, equalTo("test@test"))
    }

    @Test
    fun updateExistingEventByUidWithoutDuplicating() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(CalendarContract.Calendars.CONTENT_URI, any(), any(), any(), any()) } returns calendarsCursor()
        every { resolver.query(CalendarContract.Events.CONTENT_URI, any(), any(), any(), any()) } returns eventIdCursor(5L)

        val values = slot<ContentValues>()
        every { resolver.update(CalendarContract.Events.CONTENT_URI, capture(values), any(), any()) } returns 1
        every { resolver.delete(CalendarContract.Reminders.CONTENT_URI, any(), any()) } returns 0
        every { resolver.delete(CalendarContract.Attendees.CONTENT_URI, any(), any()) } returns 0
        every { resolver.insert(CalendarContract.Reminders.CONTENT_URI, any()) } returns null
        every { resolver.insert(CalendarContract.Attendees.CONTENT_URI, any()) } returns null

        sourceWith(resolver).restore(recurringEvent())

        verify(exactly = 0) { resolver.insert(CalendarContract.Events.CONTENT_URI, any()) }
        assertThat(values.captured.getAsString(CalendarContract.Events.UID_2445), equalTo("uid-1"))
        assertThat(values.captured.getAsString(CalendarContract.Events.DURATION), equalTo("PT1H"))
        assertThat(values.captured.getAsString(CalendarContract.Events.DTEND), equalTo(null))
        assertThat(values.captured.containsKey(CalendarContract.Events.CALENDAR_ID), equalTo(false))
    }

    @Test
    fun insertIntoTheOriginalCalendarWhenNoEventMatches() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(CalendarContract.Calendars.CONTENT_URI, any(), ByNameSelection, any(), any())
        } returns calendarsCursor()
        every { resolver.query(CalendarContract.Events.CONTENT_URI, any(), any(), any(), any()) } answers { emptyEventIdCursor() }

        val values = slot<ContentValues>()
        every { resolver.insert(CalendarContract.Events.CONTENT_URI, capture(values)) } returns Uri.parse("content://com.android.calendar/events/7")
        every { resolver.delete(CalendarContract.Reminders.CONTENT_URI, any(), any()) } returns 0
        every { resolver.delete(CalendarContract.Attendees.CONTENT_URI, any(), any()) } returns 0
        every { resolver.insert(CalendarContract.Reminders.CONTENT_URI, any()) } returns null
        every { resolver.insert(CalendarContract.Attendees.CONTENT_URI, any()) } returns null

        sourceWith(resolver).restore(recurringEvent())

        verify(exactly = 1) { resolver.insert(CalendarContract.Events.CONTENT_URI, any()) }
        assertThat(values.captured.getAsLong(CalendarContract.Events.CALENDAR_ID), equalTo(10L))
        assertThat(values.captured.getAsString(CalendarContract.Events.UID_2445), equalTo("uid-1"))
    }

    @Test
    fun fallBackToAWritableCalendarWhenTheNamedOneIsAbsent() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(CalendarContract.Calendars.CONTENT_URI, any(), ByNameSelection, any(), any())
        } returns emptyCalendarsCursor()
        every {
            resolver.query(CalendarContract.Calendars.CONTENT_URI, any(), FallbackSelection, any(), any())
        } returns calendarIdCursor(99L)
        every { resolver.query(CalendarContract.Events.CONTENT_URI, any(), any(), any(), any()) } answers { emptyEventIdCursor() }

        val values = slot<ContentValues>()
        every { resolver.insert(CalendarContract.Events.CONTENT_URI, capture(values)) } returns Uri.parse("content://com.android.calendar/events/7")
        every { resolver.delete(CalendarContract.Reminders.CONTENT_URI, any(), any()) } returns 0
        every { resolver.delete(CalendarContract.Attendees.CONTENT_URI, any(), any()) } returns 0
        every { resolver.insert(CalendarContract.Reminders.CONTENT_URI, any()) } returns null
        every { resolver.insert(CalendarContract.Attendees.CONTENT_URI, any()) } returns null

        sourceWith(resolver).restore(recurringEvent())

        assertThat(values.captured.getAsLong(CalendarContract.Events.CALENDAR_ID), equalTo(99L))
        assertThat(values.captured.getAsString(CalendarContract.Events.UID_2445), equalTo("uid-1"))
    }

    @Test
    fun skipWhenNoWritableCalendarExists() {
        val resolver = mockk<ContentResolver>()
        every {
            resolver.query(CalendarContract.Calendars.CONTENT_URI, any(), ByNameSelection, any(), any())
        } returns emptyCalendarsCursor()
        every {
            resolver.query(CalendarContract.Calendars.CONTENT_URI, any(), FallbackSelection, any(), any())
        } returns emptyCalendarsCursor()

        assertThrows(IllegalStateException::class.java) { sourceWith(resolver).restore(recurringEvent()) }

        verify(exactly = 0) { resolver.insert(CalendarContract.Events.CONTENT_URI, any()) }
    }

    private fun eventsCursor(): MatrixCursor =
        MatrixCursor(EventColumns).apply {
            addRow(
                arrayOf<Any?>(
                    1L, "uid-1", "test", "test", "test", null, 1000L, 2000L, null, 0,
                    "UTC", null, null, null, null, null, 0, 0, 0, 1
                )
            )
        }

    private fun remindersCursor(): MatrixCursor =
        MatrixCursor(arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD)).apply {
            addRow(arrayOf<Any?>(10, 1))
        }

    private fun attendeesCursor(): MatrixCursor =
        MatrixCursor(
            arrayOf(
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.ATTENDEE_STATUS
            )
        ).apply {
            addRow(arrayOf<Any?>("test", "test@test", 1, 1, 1))
        }

    private fun calendarsCursor(): MatrixCursor =
        MatrixCursor(arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)).apply {
            addRow(arrayOf<Any?>(10L, "test"))
        }

    private fun calendarIdCursor(id: Long): MatrixCursor =
        MatrixCursor(arrayOf(CalendarContract.Calendars._ID)).apply { addRow(arrayOf<Any?>(id)) }

    private fun emptyCalendarsCursor(): MatrixCursor =
        MatrixCursor(arrayOf(CalendarContract.Calendars._ID))

    private fun eventIdCursor(id: Long): MatrixCursor =
        MatrixCursor(arrayOf(CalendarContract.Events._ID)).apply { addRow(arrayOf<Any?>(id)) }

    private fun emptyEventIdCursor(): MatrixCursor =
        MatrixCursor(arrayOf(CalendarContract.Events._ID))

    private fun recurringEvent(): CalendarEvent =
        CalendarEvent(
            id = "uid-1",
            calendar = "test",
            title = "test",
            description = null,
            location = null,
            start = 1000L,
            end = 2000L,
            duration = "PT1H",
            allDay = false,
            timezone = "UTC",
            endTimezone = null,
            rrule = "FREQ=DAILY",
            rdate = null,
            exdate = null,
            organizer = null,
            availability = 0,
            accessLevel = 0,
            status = 0,
            hasAlarm = true,
            reminders = listOf(CalendarReminder(minutes = 10, method = 1)),
            attendees = listOf(CalendarAttendee(name = "test", email = "test@test", relationship = 1, type = 1, status = 1))
        )

    companion object {
        private val ByNameSelection =
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"

        private val FallbackSelection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"

        private val EventColumns: Array<String> = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.HAS_ALARM
        )
    }
}
