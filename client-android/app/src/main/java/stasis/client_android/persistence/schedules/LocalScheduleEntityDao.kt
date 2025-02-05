package stasis.client_android.persistence.schedules

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import stasis.client_android.lib.model.server.schedules.ScheduleId

@Dao
interface LocalScheduleEntityDao {
    @Query("SELECT * FROM local_schedules")
    fun get(): LiveData<List<LocalScheduleEntity>>

    @Query("SELECT * FROM local_schedules")
    suspend fun getAsync(): List<LocalScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LocalScheduleEntity)

    @Query("DELETE FROM local_schedules WHERE id == :id")
    suspend fun delete(id: ScheduleId)

    @Query("DELETE FROM local_schedules")
    suspend fun clear()
}