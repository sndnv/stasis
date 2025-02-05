package stasis.client_android.persistence.schedules

import androidx.room.Entity
import androidx.room.PrimaryKey
import stasis.client_android.lib.model.server.schedules.ScheduleId
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@Entity(tableName = "local_schedules")
data class LocalScheduleEntity(
    @PrimaryKey()
    val id: ScheduleId,
    val info: String,
    val start: LocalDateTime,
    val interval: Duration,
    val created: Instant,
)