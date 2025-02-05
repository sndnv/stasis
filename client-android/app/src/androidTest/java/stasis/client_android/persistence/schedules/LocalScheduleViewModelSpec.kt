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
import stasis.client_android.persistence.Converters.Companion.asSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)

class LocalScheduleViewModelSpec {
    @Test
    fun createLocalScheduleEntities() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                eventually {
                    assertThat(
                        model.configured.await().map { it.copy(id = entity.id) },
                        equalTo(listOf(entity.asSchedule()))
                    )
                }
            }
        }
    }

    @Test
    fun createLocalSchedules() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity.asSchedule()).await()

                eventually {
                    assertThat(
                        model.configured.await().map { it.copy(id = entity.id) },
                        equalTo(listOf(entity.asSchedule()))
                    )
                }
            }
        }
    }

    @Test
    fun retrieveLocalScheduleEntities() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                assertThat(
                    model.configured.await(),
                    equalTo(listOf(entity.asSchedule()))
                )
            }
        }
    }

    @Test
    fun deleteLocalScheduleEntities() {
        withModel { model ->
            assertThat(model.configured.await(), equalTo(emptyList()))

            runBlocking {
                model.put(entity).await()

                eventually {
                    assertThat(
                        model.configured.await(),
                        equalTo(listOf(entity.asSchedule()))
                    )
                }

                model.delete(entity.id)

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
                model.put(entity.copy(id = UUID.randomUUID())).await()
                model.put(entity.copy(id = UUID.randomUUID())).await()
                model.put(entity.copy(id = UUID.randomUUID())).await()

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

    private val entity = LocalScheduleEntity(
        id = UUID.randomUUID(),
        info = "test-schedule",
        start = LocalDateTime.now(),
        interval = Duration.ofSeconds(60),
        created = Instant.now()
    )

    private fun withModel(f: (model: LocalScheduleViewModel) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = LocalScheduleViewModel(application)
        val db = LocalScheduleEntityDatabase.getInstance(application)

        try {
            db.clearAllTables()
            f(model)
        } finally {
            db.clearAllTables()
        }
    }
}
