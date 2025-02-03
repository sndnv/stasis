package stasis.client_android.persistence.schedules

import androidx.room.Entity
import androidx.room.PrimaryKey
import stasis.client_android.lib.model.server.schedules.ScheduleId

@Entity(tableName = "active_schedules")
data class ActiveScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val schedule: ScheduleId,
    val type: String,
    val data: String?
)
