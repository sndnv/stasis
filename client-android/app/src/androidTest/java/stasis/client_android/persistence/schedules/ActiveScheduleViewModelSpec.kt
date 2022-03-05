package stasis.client_android.persistence.schedules

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
import stasis.client_android.persistence.Converters.Companion.asActiveSchedule
import java.util.UUID

@RunWith(AndroidJUnit4::class)

class ActiveScheduleViewModelSpec {
    @Test
    fun createActiveScheduleEntities() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                eventually {
                    assertThat(
                        model.configured.await().map { it.copy(id = entity.id) },
                        equalTo(listOf(entity.asActiveSchedule()))
                    )
                }
            }
        }
    }

    @Test
    fun createActiveSchedules() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity.asActiveSchedule()).await()

                eventually {
                    assertThat(
                        model.configured.await().map { it.copy(id = entity.id) },
                        equalTo(listOf(entity.asActiveSchedule()))
                    )
                }
            }
        }
    }

    @Test
    fun retrieveActiveScheduleEntities() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                assertThat(
                    model.configured.await(),
                    equalTo(listOf(entity.asActiveSchedule()))
                )
            }
        }
    }

    @Test
    fun deleteActiveScheduleEntities() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                val scheduleId = model.put(entity).await()

                eventually {
                    assertThat(
                        model.configured.await(),
                        equalTo(listOf(entity.copy(id = scheduleId).asActiveSchedule()))
                    )
                }

                model.delete(scheduleId)

                eventually {
                    assertThat(model.configured.await(), equalTo(emptyList()))
                }
            }
        }
    }

    @Test
    fun clearDatabase() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity.copy(id = 1)).await()
                model.put(entity.copy(id = 2)).await()
                model.put(entity.copy(id = 3)).await()

                eventually {
                    assertThat(
                        model.configured.await().size,
                        equalTo(3)
                    )
                }

                model.clear()

                eventually {
                    assertThat(model.configured.await(), equalTo(emptyList()))
                }
            }
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

    private fun withModel(f: (model: ActiveScheduleViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = ActiveScheduleViewModel(application)
        val db = ActiveScheduleEntityDatabase.getInstance(application)

        try {
            db.clearAllTables()
            f(model)
        } finally {
            db.clearAllTables()
        }
    }
}
