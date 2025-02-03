package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.jetbrains.annotations.TestOnly
import stasis.client_android.persistence.Converters

@Database(entities = [ActiveScheduleEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ActiveScheduleEntityDatabase : RoomDatabase() {
    abstract fun dao(): ActiveScheduleEntityDao

    companion object {
        private const val DefaultDatabase: String = "active_schedules.db"
        private const val PreviousDefaultDatabase: String = "schedules.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules RENAME TO active_schedules")
            }
        }

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

        private fun build(context: Context, database: String): ActiveScheduleEntityDatabase {
            context.databaseList().filter { it.startsWith(PreviousDefaultDatabase) }.forEach {
                val databaseFile = context.getDatabasePath(it)
                if (databaseFile.exists()) {
                    val updatedName = databaseFile.name.replace(PreviousDefaultDatabase, DefaultDatabase)
                    val updatedDatabaseFile = java.io.File(databaseFile.parentFile, updatedName)
                    databaseFile.renameTo(updatedDatabaseFile)
                }
            }

            return Room.databaseBuilder(context.applicationContext, ActiveScheduleEntityDatabase::class.java, database)
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
