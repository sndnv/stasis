package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.jetbrains.annotations.TestOnly
import stasis.client_android.persistence.Converters

@Database(entities = [ActiveScheduleEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ActiveScheduleEntityDatabase : RoomDatabase() {
    abstract fun dao(): ActiveScheduleEntityDao

    companion object {
        private const val DefaultDatabase: String = "schedules.db"

        @Volatile
        private var INSTANCE: ActiveScheduleEntityDatabase? = null

        fun getInstance(context: Context) =
            getInstance(context, DefaultDatabase)

        fun getInstance(context: Context, database: String): ActiveScheduleEntityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, database).also { INSTANCE = it }
            }

        @TestOnly
        fun setInstance(instance: ActiveScheduleEntityDatabase): Unit =
            synchronized(this) {
                require(INSTANCE == null) { "ActiveScheduleEntityDatabase instance already exists" }
                INSTANCE = instance
            }

        private fun build(context: Context, database: String) =
            Room.databaseBuilder(
                context.applicationContext,
                ActiveScheduleEntityDatabase::class.java,
                database
            ).build()
    }
}
