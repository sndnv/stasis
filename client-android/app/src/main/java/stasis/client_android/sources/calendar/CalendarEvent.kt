package stasis.client_android.sources.calendar

data class CalendarEvent(
    val id: String,
    val calendar: String,
    val title: String,
    val description: String?,
    val location: String?,
    val start: Long,
    val end: Long?,
    val duration: String?,
    val allDay: Boolean,
    val timezone: String?,
    val endTimezone: String?,
    val rrule: String?,
    val rdate: String?,
    val exdate: String?,
    val organizer: String?,
    val availability: Int,
    val accessLevel: Int,
    val status: Int,
    val hasAlarm: Boolean,
    val reminders: List<CalendarReminder>,
    val attendees: List<CalendarAttendee>
)
