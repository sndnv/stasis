package stasis.client_android.persistence.rules

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

@RunWith(AndroidJUnit4::class)
class RuleEntityDaoSpec {
    @Test
    fun createRuleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun retrieveRuleEntities() {
        withDao { dao ->
            assertThat(dao.get().await(), equalTo(emptyList()))

            runBlocking { dao.put(entity) }
            assertThat(dao.get().await(), equalTo(listOf(entity)))
        }
    }

    @Test
    fun retrieveAsyncRuleEntities() {
        withDao { dao ->
            runBlocking {
                assertThat(dao.getAsync(), equalTo(emptyList()))

                dao.put(entity)
                assertThat(dao.getAsync(), equalTo(listOf(entity)))
            }
        }
    }

    @Test
    fun deleteRuleEntities() {
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

    private val entity = RuleEntity(
        id = 1,
        operation = stasis.client_android.lib.collection.rules.Rule.Operation.Include,
        directory = "/a/b/c",
        pattern = ".*"
    )

    private fun withDao(f: (dao: RuleEntityDao) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, RuleEntityDatabase::class.java).build()

        try {
            f(db.dao())
        } finally {
            db.close()
        }
    }
}
