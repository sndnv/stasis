package stasis.client_android.persistence.rules

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RuleEntityDao {
    @Query("SELECT * FROM rules")
    fun get(): LiveData<List<RuleEntity>>

    @Query("SELECT * FROM rules")
    suspend fun getAsync(): List<RuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: RuleEntity): Long

    @Query("DELETE FROM rules WHERE id == :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM rules")
    suspend fun clear()
}
