package stasis.client_android.persistence.schedules

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ActiveScheduleEntityDao {
    @Query("SELECT * FROM active_schedules")
    fun get(): LiveData<List<ActiveScheduleEntity>>

    @Query("SELECT * FROM active_schedules")
    suspend fun getAsync(): List<ActiveScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ActiveScheduleEntity): Long

    @Query("DELETE FROM active_schedules WHERE id == :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM active_schedules")
    suspend fun clear()
}
