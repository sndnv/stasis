package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import stasis.client_android.lib.model.server.schedules.Schedule
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.persistence.Converters.Companion.asLocalScheduleEntity
import stasis.client_android.persistence.Converters.Companion.asSchedule

class LocalScheduleRepository(private val dao: LocalScheduleEntityDao) {
    val schedules: LiveData<List<Schedule>> =
        dao.get().map { entities -> entities.map { it.asSchedule() }.sortedBy { it.created } }

    suspend fun schedulesAsync(): List<Schedule> =
        dao.getAsync().map { it.asSchedule() }.sortedBy { it.id }

    suspend fun put(schedule: Schedule) =
        put(schedule.asLocalScheduleEntity())

    suspend fun put(schedule: LocalScheduleEntity) =
        dao.put(schedule)

    suspend fun delete(id: ScheduleId) =
        dao.delete(id)

    suspend fun clear() =
        dao.clear()

    companion object {
        operator fun invoke(context: Context): LocalScheduleRepository {
            val dao = LocalScheduleEntityDatabase.getInstance(context).dao()
            return LocalScheduleRepository(dao)
        }
    }
}
