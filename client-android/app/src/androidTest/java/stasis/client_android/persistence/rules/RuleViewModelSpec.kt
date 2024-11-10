package stasis.client_android.persistence.rules

import android.app.Application
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
import stasis.client_android.eventually
import stasis.client_android.persistence.Converters.Companion.asRule

@RunWith(AndroidJUnit4::class)
class RuleViewModelSpec {
    @Test
    fun createRuleEntities() {
        withModel { model ->
            assertThat(model.rules.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                eventually {
                    assertThat(
                        model.rules.await().map { it.copy(id = entity.id) },
                        equalTo(listOf(entity.asRule()))
                    )
                }
            }
        }
    }

    @Test
    fun createRules() {
        withModel { model ->
            assertThat(model.rules.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity.asRule()).await()

                eventually {
                    assertThat(
                        model.rules.await().map { it.copy(id = entity.id) },
                        equalTo(listOf(entity.asRule()))
                    )
                }
            }
        }
    }

    @Test
    fun retrieveRuleEntities() {
        withModel { model ->
            assertThat(model.rules.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                assertThat(
                    model.rules.await(),
                    equalTo(listOf(entity.asRule()))
                )
            }
        }
    }

    @Test
    fun deleteRuleEntities() {
        withModel { model ->
            assertThat(model.rules.await(), equalTo(emptyList()))

            runBlocking {
                val ruleId = model.put(entity).await()

                eventually {
                    assertThat(
                        model.rules.await(),
                        equalTo(listOf(entity.copy(id = ruleId).asRule()))
                    )
                }

                model.delete(ruleId)

                eventually {
                    assertThat(model.rules.await(), equalTo(emptyList()))
                }
            }
        }
    }

    @Test
    fun bootstrapDatabase() {
        withModel { model ->
            assertThat(model.rules.await(), equalTo(emptyList()))

            runBlocking {
                model.bootstrap().await()
            }

            assertThat(
                model.rules.await().map { it.copy(id = 0) },
                equalTo(RulesConfig.DefaultRules.map { it.asRule().copy(id = 0) })
            )
        }
    }

    @Test
    fun clearDatabase() {
        withModel { model ->
            assertThat(model.rules.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity.copy(id = 1)).await()
                model.put(entity.copy(id = 2)).await()
                model.put(entity.copy(id = 3)).await()

                eventually {
                    assertThat(
                        model.rules.await().size,
                        equalTo(3)
                    )
                }

                model.clear()

                eventually {
                    assertThat(model.rules.await(), equalTo(emptyList()))
                }
            }
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val entity = RuleEntity(
        id = 1,
        operation = stasis.client_android.lib.collection.rules.Rule.Operation.Include,
        directory = "/a/b/c",
        pattern = ".*",
        definition = null
    )

    private fun withModel(f: (model: RuleViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = RuleViewModel(application)
        val db = RuleEntityDatabase.getInstance(application)

        try {
            db.clearAllTables()
            f(model)
        } finally {
            db.clearAllTables()
        }
    }
}
