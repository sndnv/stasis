package stasis.client_android.persistence.schedules

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.await
import stasis.client_android.lib.model.server.schedules.ScheduleId
import stasis.client_android.persistence.Converters.Companion.asSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class LocalScheduleRepositorySpec {
    @Test
    fun createLocalScheduleEntities() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asSchedule())))
    }

    @Test
    fun createLocalSchedules() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity.asSchedule()) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asSchedule())))
    }

    @Test
    fun retrieveLocalScheduleEntities() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asSchedule())))
    }

    @Test
    fun retrieveAsyncLocalScheduleEntities() {
        val repo = createRepo()
        runBlocking {
            assertThat(repo.schedulesAsync(), equalTo(emptyList()))

            repo.put(entity)
            assertThat(repo.schedulesAsync(), equalTo(listOf(entity.asSchedule())))
        }
    }

    @Test
    fun deleteLocalScheduleEntities() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asSchedule())))

        runBlocking { repo.delete(entity.id) }
        assertThat(repo.schedules.await(), equalTo(emptyList()))
    }

    @Test
    fun clearDatabase() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking {
            repo.put(entity.copy(id = UUID.randomUUID()))
            repo.put(entity.copy(id = UUID.randomUUID()))
            repo.put(entity.copy(id = UUID.randomUUID()))
        }

        assertThat(repo.schedules.await().size, equalTo(3))

        runBlocking { repo.clear() }

        assertThat(repo.schedules.await(), equalTo(emptyList()))
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

    private fun createRepo(): LocalScheduleRepository {
        val dao = object : LocalScheduleEntityDao {
            val entities = ConcurrentHashMap<ScheduleId, LocalScheduleEntity>()
            val data = MutableLiveData<List<LocalScheduleEntity>>(emptyList())

            override fun get(): LiveData<List<LocalScheduleEntity>> = data

            override suspend fun getAsync(): List<LocalScheduleEntity> = data.await()

            override suspend fun put(entity: LocalScheduleEntity) {
                entities[entity.id] = entity
                data.value = entities.toList().map { it.second }
            }

            override suspend fun delete(id: ScheduleId) {
                entities -= id
                data.value = entities.toList().map { it.second }
            }

            override suspend fun clear() {
                entities.clear()
                data.value = emptyList()
            }
        }

        return LocalScheduleRepository(dao)
    }
}
