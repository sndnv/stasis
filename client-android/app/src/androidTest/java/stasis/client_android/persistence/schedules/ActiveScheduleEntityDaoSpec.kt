package stasis.client_android.persistence.schedules

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
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
class ActiveScheduleEntityDaoSpec {
    @Test
    fun createActiveScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun retrieveActiveScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun retrieveAsyncActiveScheduleEntities() {
        withDao { dao ->
            runBlocking {
                assertThat(dao.getAsync(), equalTo(emptyList()))

                dao.put(entity)

                assertThat(dao.getAsync(), equalTo(listOf(entity)))
            }
        }
    }

    @Test
    fun deleteActiveScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))

            runBlocking { dao.delete(entity.id) }
            assertThat(dao.get().await(), equalTo(emptyList()))
        }
    }

    @Test
    fun clearDatabase() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking {
                dao.put(entity.copy(id = 1))
                dao.put(entity.copy(id = 2))
                dao.put(entity.copy(id = 3))
            }

            assertThat(dao.get().await().size, equalTo(3))

            runBlocking { dao.clear() }

            assertThat(dao.get().await(), equalTo(emptyList()))
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

    private fun withDao(f: (dao: ActiveScheduleEntityDao) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room.inMemoryDatabaseBuilder(context, ActiveScheduleEntityDatabase::class.java).build()

        try {
            f(db.dao())
        } finally {
            db.close()
        }
    }
}
