package stasis.client_android.sources.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import stasis.client_android.sources.Cursors.collect
import stasis.client_android.sources.Cursors.queryFirst
import stasis.client_android.sources.Cursors.queryList
import stasis.client_android.sources.EntityPreview
import stasis.client_android.sources.ExportedContent
import stasis.client_android.sources.LibrarySource
import java.time.Instant
import java.util.TimeZone

class CalendarSource(
    private val resolver: ContentResolver,
    private val hasReadPermission: () -> Boolean,
    private val hasWritePermission: () -> Boolean
) : LibrarySource<CalendarEvent> {
    override val scheme: String = Scheme

    override val recordClass: Class<CalendarEvent> = CalendarEvent::class.java

    override fun hasReadPermission(): Boolean = hasReadPermission.invoke()

    override fun hasWritePermission(): Boolean = hasWritePermission.invoke()

    override fun list(): List<CalendarEvent> =
        resolver.queryList(
            CalendarContract.Events.CONTENT_URI,
            Projection,
            "${CalendarContract.Events.DELETED} = 0",
            null,
            null
        ) { cursor ->
            val rowIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val uidIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.UID_2445)
            val calendarIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
            val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val durationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DURATION)
            val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val timezoneIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)
            val endTimezoneIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_END_TIMEZONE)
            val rruleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
            val rdateIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.RDATE)
            val exdateIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.EXDATE)
            val organizerIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ORGANIZER)
            val availabilityIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.AVAILABILITY)
            val accessLevelIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ACCESS_LEVEL)
            val statusIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.STATUS)
            val hasAlarmIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.HAS_ALARM)

            cursor.collect { row ->
                val rowId = row.getLong(rowIdIndex)
                val uid = row.getStringOrNull(uidIndex)

                CalendarEvent(
                    id = uid?.takeIf { it.isNotBlank() } ?: rowId.toString(),
                    calendar = row.getStringOrNull(calendarIndex).orEmpty(),
                    title = row.getStringOrNull(titleIndex).orEmpty(),
                    description = row.getStringOrNull(descriptionIndex),
                    location = row.getStringOrNull(locationIndex),
                    start = row.getLongOrNull(startIndex) ?: 0L,
                    end = row.getLongOrNull(endIndex),
                    duration = row.getStringOrNull(durationIndex),
                    allDay = (row.getLongOrNull(allDayIndex) ?: 0L) != 0L,
                    timezone = row.getStringOrNull(timezoneIndex),
                    endTimezone = row.getStringOrNull(endTimezoneIndex),
                    rrule = row.getStringOrNull(rruleIndex),
                    rdate = row.getStringOrNull(rdateIndex),
                    exdate = row.getStringOrNull(exdateIndex),
                    organizer = row.getStringOrNull(organizerIndex),
                    availability = row.getIntOrNull(availabilityIndex) ?: 0,
                    accessLevel = row.getIntOrNull(accessLevelIndex) ?: 0,
                    status = row.getIntOrNull(statusIndex) ?: 0,
                    hasAlarm = (row.getLongOrNull(hasAlarmIndex) ?: 0L) != 0L,
                    reminders = reminders(rowId),
                    attendees = attendees(rowId)
                )
            }
        }

    override fun restore(record: CalendarEvent) {
        val calendarId = targetCalendarId(record)
        val existing = findEvent(record, calendarId)

        if (existing != null) {
            resolver.update(CalendarContract.Events.CONTENT_URI, eventValues(record, calendarId = null), ID_SELECTION, arrayOf(existing.toString()))
            replaceChildren(existing, record)
        } else {
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, eventValues(record, calendarId = calendarId))
                ?: throw IllegalStateException("Failed to insert event [${record.id}]")
            replaceChildren(ContentUris.parseId(uri), record)
        }
    }

    override fun id(record: CalendarEvent): String = record.id

    override fun displayName(record: CalendarEvent): String = record.title

    override fun attributes(record: CalendarEvent): Map<String, String> =
        mapOf("name" to record.title, "calendar" to record.calendar)

    override fun describe(record: CalendarEvent): EntityPreview {
        val event = buildList {
            add(EntityPreview.Field("Title", record.title.ifBlank { "(no title)" }))
            if (record.calendar.isNotBlank()) add(EntityPreview.Field("Calendar", record.calendar))
            add(EntityPreview.Field("Start", formatTimestamp(record.start)))
            when {
                record.end != null -> add(EntityPreview.Field("End", formatTimestamp(record.end)))
                record.duration != null -> add(EntityPreview.Field("Duration", record.duration))
            }
            if (record.allDay) add(EntityPreview.Field("All day", "Yes"))
            record.location?.takeIf { it.isNotBlank() }?.let { add(EntityPreview.Field("Location", it)) }
            record.description?.takeIf { it.isNotBlank() }?.let { add(EntityPreview.Field("Description", it)) }
            record.rrule?.takeIf { it.isNotBlank() }?.let { add(EntityPreview.Field("Repeats", it)) }
            record.organizer?.takeIf { it.isNotBlank() }?.let { add(EntityPreview.Field("Organizer", it)) }
        }

        val reminders = record.reminders.map {
            EntityPreview.Field("Reminder", "${it.minutes} minutes before")
        }

        val attendees = record.attendees.map { attendee ->
            val label = attendee.name?.takeIf { it.isNotBlank() }
                ?: attendee.email?.takeIf { it.isNotBlank() }
                ?: "Attendee"
            EntityPreview.Field(label, attendee.email.orEmpty())
        }

        return EntityPreview(
            sections = listOf(
                EntityPreview.Section("Event", event),
                EntityPreview.Section("Reminders", reminders),
                EntityPreview.Section("Attendees", attendees)
            ).filter { it.fields.isNotEmpty() }
        )
    }

    override fun export(record: CalendarEvent): ExportedContent =
        ExportedContent(extension = "ics", mimeType = "text/calendar", bytes = CalendarICal.toICal(record))

    private fun formatTimestamp(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    private fun reminders(eventId: Long): List<CalendarReminder> =
        resolver.queryList(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            "${CalendarContract.Reminders.MINUTES} ASC, ${CalendarContract.Reminders.METHOD} ASC"
        ) { cursor ->
            val minutesIndex = cursor.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES)
            val methodIndex = cursor.getColumnIndexOrThrow(CalendarContract.Reminders.METHOD)
            cursor.collect { row ->
                CalendarReminder(
                    minutes = row.getIntOrNull(minutesIndex) ?: 0,
                    method = row.getIntOrNull(methodIndex) ?: 0
                )
            }
        }

    private fun attendees(eventId: Long): List<CalendarAttendee> =
        resolver.queryList(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.ATTENDEE_TYPE,
                CalendarContract.Attendees.ATTENDEE_STATUS
            ),
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            "${CalendarContract.Attendees.ATTENDEE_EMAIL} ASC, ${CalendarContract.Attendees.ATTENDEE_NAME} ASC"
        ) { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_NAME)
            val emailIndex = cursor.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_EMAIL)
            val relationshipIndex = cursor.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP)
            val typeIndex = cursor.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_TYPE)
            val statusIndex = cursor.getColumnIndexOrThrow(CalendarContract.Attendees.ATTENDEE_STATUS)
            cursor.collect { row ->
                CalendarAttendee(
                    name = row.getStringOrNull(nameIndex),
                    email = row.getStringOrNull(emailIndex),
                    relationship = row.getIntOrNull(relationshipIndex) ?: 0,
                    type = row.getIntOrNull(typeIndex) ?: 0,
                    status = row.getIntOrNull(statusIndex) ?: 0
                )
            }
        }

    private fun findEvent(record: CalendarEvent, calendarId: Long): Long? =
        byUid(record.id, calendarId) ?: resolver.queryFirst(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} = ? AND ${CalendarContract.Events.TITLE} = ?",
            arrayOf(calendarId.toString(), record.start.toString(), record.title),
            null
        ) { it.getLongOrNull(it.getColumnIndexOrThrow(CalendarContract.Events._ID)) }

    private fun byUid(uid: String, calendarId: Long): Long? =
        resolver.queryFirst(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.UID_2445} = ?",
            arrayOf(calendarId.toString(), uid),
            null
        ) { it.getLongOrNull(it.getColumnIndexOrThrow(CalendarContract.Events._ID)) }

    private fun replaceChildren(eventId: Long, record: CalendarEvent) {
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
        resolver.delete(
            CalendarContract.Attendees.CONTENT_URI,
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )

        record.reminders.forEach { reminder ->
            resolver.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, reminder.minutes)
                    put(CalendarContract.Reminders.METHOD, reminder.method)
                }
            )
        }

        record.attendees.forEach { attendee ->
            resolver.insert(
                CalendarContract.Attendees.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Attendees.EVENT_ID, eventId)
                    put(CalendarContract.Attendees.ATTENDEE_NAME, attendee.name)
                    put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendee.email)
                    put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, attendee.relationship)
                    put(CalendarContract.Attendees.ATTENDEE_TYPE, attendee.type)
                    put(CalendarContract.Attendees.ATTENDEE_STATUS, attendee.status)
                }
            )
        }
    }

    private fun eventValues(record: CalendarEvent, calendarId: Long?): ContentValues = ContentValues().apply {
        calendarId?.let { put(CalendarContract.Events.CALENDAR_ID, it) }
        put(CalendarContract.Events.UID_2445, record.id)
        put(CalendarContract.Events.TITLE, record.title)
        put(CalendarContract.Events.DESCRIPTION, record.description)
        put(CalendarContract.Events.EVENT_LOCATION, record.location)
        put(CalendarContract.Events.DTSTART, record.start)
        put(CalendarContract.Events.ALL_DAY, if (record.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, record.timezone ?: TimeZone.getDefault().id)
        put(CalendarContract.Events.EVENT_END_TIMEZONE, record.endTimezone)
        put(CalendarContract.Events.RRULE, record.rrule)
        put(CalendarContract.Events.RDATE, record.rdate)
        put(CalendarContract.Events.EXDATE, record.exdate)
        put(CalendarContract.Events.ORGANIZER, record.organizer)
        put(CalendarContract.Events.AVAILABILITY, record.availability)
        put(CalendarContract.Events.ACCESS_LEVEL, record.accessLevel)
        put(CalendarContract.Events.STATUS, record.status)
        put(CalendarContract.Events.HAS_ALARM, if (record.hasAlarm) 1 else 0)

        when {
            record.rrule != null && record.duration != null -> {
                put(CalendarContract.Events.DURATION, record.duration)
                putNull(CalendarContract.Events.DTEND)
            }

            record.end != null -> {
                put(CalendarContract.Events.DTEND, record.end)
                putNull(CalendarContract.Events.DURATION)
            }

            record.duration != null -> {
                put(CalendarContract.Events.DURATION, record.duration)
                putNull(CalendarContract.Events.DTEND)
            }

            else -> {
                put(CalendarContract.Events.DTEND, record.start)
                putNull(CalendarContract.Events.DURATION)
            }
        }
    }

    private fun targetCalendarId(record: CalendarEvent): Long =
        record.calendar.takeIf { it.isNotBlank() }?.let { calendarByName(it) }
            ?: fallbackCalendarId()
            ?: throw IllegalStateException("No writable calendar available to restore event [${record.id}]")

    private fun calendarByName(name: String): Long? =
        resolver.queryFirst(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(name, WritableAccessLevel.toString()),
            null
        ) { it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)) }

    private fun fallbackCalendarId(): Long? =
        resolver.queryFirst(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(WritableAccessLevel.toString()),
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, " +
                "${CalendarContract.Calendars.VISIBLE} DESC, ${CalendarContract.Calendars._ID} ASC"
        ) { it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)) }

    companion object {
        const val Scheme: String = "calendar"

        private val WritableAccessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR

        private const val ID_SELECTION: String = "${CalendarContract.Events._ID} = ?"

        private val Projection: Array<String> = arrayOf(
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
