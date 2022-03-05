package stasis.client_android.persistence.rules

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
import stasis.client_android.persistence.Converters.Companion.asRule
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class RuleRepositorySpec {
    @Test
    fun createRuleEntities() {
        val repo = createRepo()

        assertThat(repo.rules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.rules.await(), equalTo(listOf(entity.asRule())))
    }

    @Test
    fun createRules() {
        val repo = createRepo()

        assertThat(repo.rules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity.asRule()) }
        assertThat(repo.rules.await(), equalTo(listOf(entity.asRule())))
    }

    @Test
    fun retrieveRuleEntities() {
        val repo = createRepo()

        assertThat(repo.rules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.rules.await(), equalTo(listOf(entity.asRule())))
    }

    @Test
    fun retrieveAsyncRuleEntities() {
        val repo = createRepo()
        runBlocking {
            assertThat(repo.rulesAsync(), equalTo(emptyList()))

            repo.put(entity)

            assertThat(repo.rulesAsync(), equalTo(listOf(entity.asRule())))
        }
    }

    @Test
    fun deleteRuleEntities() {
        val repo = createRepo()

        assertThat(repo.rules.await(), equalTo(emptyList()))

        runBlocking { repo.put(entity) }
        assertThat(repo.rules.await(), equalTo(listOf(entity.asRule())))

        runBlocking { repo.delete(entity.id) }
        assertThat(repo.rules.await(), equalTo(emptyList()))
    }

    @Test
    fun bootstrapDatabase() {
        val repo = createRepo()

        assertThat(repo.rules.await(), equalTo(emptyList()))

        runBlocking {
            repo.bootstrap()
        }

        assertThat(
            repo.rules.await().map { it.copy(id = 0) },
            equalTo(RuleRepository.DefaultRules.map { it.asRule().copy(id = 0) })
        )
    }

    @Test
    fun clearDatabase() {
        val repo = createRepo()

        assertThat(repo.rules.await(), equalTo(emptyList()))

        runBlocking {
            repo.put(entity.copy(id = 1))
            repo.put(entity.copy(id = 2))
            repo.put(entity.copy(id = 3))
        }

        assertThat(repo.rules.await().size, equalTo(3))

        runBlocking { repo.clear() }

        assertThat(repo.rules.await(), equalTo(emptyList()))
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val entity = RuleEntity(
        id = 1,
        operation = stasis.client_android.lib.collection.rules.Rule.Operation.Include,
        directory = "/a/b/c",
        pattern = ".*"
    )

    private fun createRepo(): RuleRepository {
        val dao = object : RuleEntityDao {
            val entities = ConcurrentHashMap<Long, RuleEntity>()
            val data = MutableLiveData<List<RuleEntity>>(emptyList())

            override fun get(): LiveData<List<RuleEntity>> = data

            override suspend fun getAsync(): List<RuleEntity> = data.await()

            override suspend fun put(entity: RuleEntity): Long {
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

        return RuleRepository(dao)
    }
}
