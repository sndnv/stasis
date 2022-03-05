package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.await
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ActiveScheduleEntityDatabaseSpec {
    @Test
    fun initializeItself() {
        withDatabase { db ->
            val dao = db.dao()

            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val entity = ActiveScheduleEntity(
        id = 1,
        schedule = UUID.randomUUID(),
        type = "expiration",
        data = null
    )

    private fun withDatabase(f: (db: ActiveScheduleEntityDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = "${UUID.randomUUID()}.db"
        val db = ActiveScheduleEntityDatabase.getInstance(context, database)

        try {
            db.clearAllTables()
            f(db)
        } finally {
            db.clearAllTables()
            db.close()
        }
    }
}
