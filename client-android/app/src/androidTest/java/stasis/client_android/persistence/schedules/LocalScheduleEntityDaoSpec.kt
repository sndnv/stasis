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
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalScheduleEntityDaoSpec {
    @Test
    fun createLocalScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun retrieveLocalScheduleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun retrieveAsyncLocalScheduleEntities() {
        withDao { dao ->
            runBlocking {
                assertThat(dao.getAsync(), equalTo(emptyList()))

                dao.put(entity)

                assertThat(dao.getAsync(), equalTo(listOf(entity)))
            }
        }
    }

    @Test
    fun deleteLocalScheduleEntities() {
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
                dao.put(entity.copy(id = UUID.randomUUID()))
                dao.put(entity.copy(id = UUID.randomUUID()))
                dao.put(entity.copy(id = UUID.randomUUID()))
            }

            assertThat(dao.get().await().size, equalTo(3))

            runBlocking { dao.clear() }

            assertThat(dao.get().await(), equalTo(emptyList()))
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val entity = LocalScheduleEntity(
        id = UUID.randomUUID(),
        info = "test-schedule",
        start = LocalDateTime.now(),
        interval = Duration.ofSeconds(60),
        created = Instant.now()
    )

    private fun withDao(f: (dao: LocalScheduleEntityDao) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db =
            Room.inMemoryDatabaseBuilder(context, LocalScheduleEntityDatabase::class.java).build()

        try {
            f(db.dao())
        } finally {
            db.close()
        }
    }
}
