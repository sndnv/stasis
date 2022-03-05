package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.persistence.Converters.Companion.asActiveSchedule
import stasis.client_android.persistence.Converters.Companion.asEntity

class ActiveScheduleRepository(private val dao: ActiveScheduleEntityDao) {
    val schedules: LiveData<List<ActiveSchedule>> =
        dao.get().map { entities -> entities.map { it.asActiveSchedule() }.sortedBy { it.id } }

    suspend fun schedulesAsync(): List<ActiveSchedule> =
        dao.getAsync().map { it.asActiveSchedule() }.sortedBy { it.id }

    suspend fun put(schedule: ActiveSchedule): Long =
        put(schedule.asEntity())

    suspend fun put(schedule: ActiveScheduleEntity): Long =
        dao.put(schedule)

    suspend fun delete(id: Long) =
        dao.delete(id)

    suspend fun clear() =
        dao.clear()

    companion object {
        operator fun invoke(context: Context): ActiveScheduleRepository {
            val dao = ActiveScheduleEntityDatabase.getInstance(context).dao()
            return ActiveScheduleRepository(dao)
        }
    }
}
