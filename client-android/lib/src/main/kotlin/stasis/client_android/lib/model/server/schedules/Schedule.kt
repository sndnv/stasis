package stasis.client_android.lib.model.server.schedules

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

data class Schedule(
    val id: ScheduleId,
    val info: String,
    val isPublic: Boolean,
    val start: LocalDateTime,
    val interval: Duration
) {
    fun nextInvocation(): LocalDateTime {
        val now = LocalDateTime.now()

        return if (start.isBefore(now)) {
            val intervalMillis = max(interval.toMillis(), 1L)

            val difference = start.until(now, ChronoUnit.MILLIS)

            val invocations = difference / intervalMillis
            start.plus((invocations + 1) * intervalMillis, ChronoUnit.MILLIS)
        } else {
            start
        }
    }
}

typealias ScheduleId = UUID
