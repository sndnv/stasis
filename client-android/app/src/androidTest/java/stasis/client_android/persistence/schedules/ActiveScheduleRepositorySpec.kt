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
import stasis.client_android.persistence.Converters.Companion.asActiveSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class ActiveScheduleRepositorySpec {
    @Test
    fun createActiveScheduleEntities() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asActiveSchedule())))
    }

    @Test
    fun createActiveSchedules() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity.asActiveSchedule()) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asActiveSchedule())))
    }

    @Test
    fun retrieveActiveScheduleEntities() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asActiveSchedule())))
    }

    @Test
    fun retrieveAsyncActiveScheduleEntities() {
        val repo = createRepo()
        runBlocking {
            assertThat(repo.schedulesAsync(), equalTo(emptyList()))

            repo.put(entity)
            assertThat(repo.schedulesAsync(), equalTo(listOf(entity.asActiveSchedule())))
        }
    }

    @Test
    fun deleteActiveScheduleEntities() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.schedules.await(), equalTo(listOf(entity.asActiveSchedule())))

        runBlocking { repo.delete(entity.id) }
        assertThat(repo.schedules.await(), equalTo(emptyList()))
    }

    @Test
    fun clearDatabase() {
        val repo = createRepo()

        assertThat(repo.schedules.await(), equalTo(emptyList()))

        runBlocking {
            repo.put(entity.copy(id = 1))
            repo.put(entity.copy(id = 2))
            repo.put(entity.copy(id = 3))
        }

        assertThat(repo.schedules.await().size, equalTo(3))

        runBlocking { repo.clear() }

        assertThat(repo.schedules.await(), equalTo(emptyList()))
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val entity = ActiveScheduleEntity(
        id = 1,
        schedule = UUID.randomUUID(),
        type = "expiration",
        data = null
    )

    private fun createRepo(): ActiveScheduleRepository {
        val dao = object : ActiveScheduleEntityDao {
            val entities = ConcurrentHashMap<Long, ActiveScheduleEntity>()
            val data = MutableLiveData<List<ActiveScheduleEntity>>(emptyList())

            override fun get(): LiveData<List<ActiveScheduleEntity>> = data

            override suspend fun getAsync(): List<ActiveScheduleEntity> = data.await()

            override suspend fun put(entity: ActiveScheduleEntity): Long {
                val id = if (entity.id == 0L) entities.size + 1L else entities.size + 1L
                entities[id] = entity.copy(id = id)
                data.value = entities.toList().map { it.second }
                return id
            }

            override suspend fun delete(id: Long) {
                entities -= id
                data.value = entities.toList().map { it.second }
            }

            override suspend fun clear() {
                entities.clear()
                data.value = emptyList()
            }
        }

        return ActiveScheduleRepository(dao)
    }
}
