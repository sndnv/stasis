package stasis.client_android.sources.calendar

data class CalendarAttendee(
    val name: String?,
    val email: String?,
    val relationship: Int,
    val type: Int,
    val status: Int
)
