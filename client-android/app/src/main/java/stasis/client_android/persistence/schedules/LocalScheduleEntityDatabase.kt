package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.jetbrains.annotations.TestOnly
import stasis.client_android.persistence.Converters

@Database(entities = [LocalScheduleEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class LocalScheduleEntityDatabase : RoomDatabase() {
    abstract fun dao(): LocalScheduleEntityDao

    companion object {
        private const val DefaultDatabase: String = "local_schedules.db"

        @Volatile
        private var INSTANCE: LocalScheduleEntityDatabase? = null

        fun getInstance(context: Context) =
            getInstance(context, DefaultDatabase)

        fun getInstance(context: Context, database: String): LocalScheduleEntityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, database).also { INSTANCE = it }
            }

        @TestOnly
        fun setInstance(instance: LocalScheduleEntityDatabase): Unit =
            synchronized(this) {
                require(INSTANCE == null) { "LocalScheduleEntityDatabase instance already exists" }
                INSTANCE = instance
            }

        private fun build(context: Context, database: String): LocalScheduleEntityDatabase {
            return Room.databaseBuilder(context.applicationContext, LocalScheduleEntityDatabase::class.java, database)
                .build()
        }
    }
}