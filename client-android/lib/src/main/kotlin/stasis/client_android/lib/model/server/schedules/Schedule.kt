package stasis.client_android.lib.model.server.schedules

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

@JsonClass(generateAdapter = true)
data class Schedule(
    val id: ScheduleId,
    val info: String,
    @field:Json(name = "is_public")
    val isPublic: Boolean,
    val start: LocalDateTime,
    val interval: Duration,
    val created: Instant,
    val updated: Instant
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
