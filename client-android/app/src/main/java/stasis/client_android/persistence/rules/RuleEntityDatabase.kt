package stasis.client_android.persistence.rules

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.jetbrains.annotations.TestOnly
import stasis.client_android.persistence.Converters

@Database(entities = [RuleEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class RuleEntityDatabase : RoomDatabase() {
    abstract fun dao(): RuleEntityDao

    companion object {
        private const val DefaultDatabase: String = "rules.db"

        @Volatile
        private var INSTANCE: RuleEntityDatabase? = null

        fun getInstance(context: Context) =
            getInstance(context, DefaultDatabase)

        fun getInstance(context: Context, database: String): RuleEntityDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, database).also { INSTANCE = it }
            }

        @TestOnly
        fun setInstance(instance: RuleEntityDatabase): Unit =
            synchronized(this) {
                require(INSTANCE == null) { "RuleEntityDatabase instance already exists" }
                INSTANCE = instance
            }

        private fun build(context: Context, database: String) =
            Room.databaseBuilder(
                context.applicationContext,
                RuleEntityDatabase::class.java,
                database
            ).build()
    }
}
