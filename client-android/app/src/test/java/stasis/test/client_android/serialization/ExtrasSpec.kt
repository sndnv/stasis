package stasis.test.client_android.serialization

import android.content.Intent
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.lib.ops.scheduling.ActiveSchedule
import stasis.client_android.lib.ops.scheduling.OperationScheduleAssignment
import stasis.client_android.serialization.Extras.putActiveSchedule
import stasis.client_android.serialization.Extras.putActiveScheduleId
import stasis.client_android.serialization.Extras.requireActiveSchedule
import stasis.client_android.serialization.Extras.requireActiveScheduleId
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class ExtrasSpec {
    @Test
    fun putAndRetrieveActiveSchedules() {
        val schedule = ActiveSchedule(
            id = 0,
            assignment = OperationScheduleAssignment.Expiration(schedule = UUID.randomUUID())
        )

        val intent = createIntent().putActiveSchedule(extra, schedule)
        assertThat(intent.getStringExtra(extra), not(equalTo(null)))

        val extractedSchedule = intent.requireActiveSchedule(extra)
        assertThat(extractedSchedule, equalTo(schedule))
    }

    @Test
    fun failToRetrieveActiveScheduleOnMissingExtra() {
        val intent = createIntent()

        try {
            intent.requireActiveSchedule(extra)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected active schedule [$extra] but none was provided"))
        }
    }

    @Test
    fun putAndRetrieveActiveScheduleIDs() {
        val activeScheduleId = 42L
        val intent = createIntent().putActiveScheduleId(extra, activeScheduleId)
        assertThat(intent.getLongExtra(extra, 0L), equalTo(activeScheduleId))

        val extractedScheduleId = intent.requireActiveScheduleId(extra)
        assertThat(extractedScheduleId, equalTo(activeScheduleId))
    }

    @Test
    fun failToPutInvalidActiveScheduleIdAsExtra() {
        val intent = createIntent()

        try {
            intent.putActiveScheduleId(extra, activeScheduleId = Long.MIN_VALUE)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Invalid active schedule ID [$extra] provided"))
        }
    }

    @Test
    fun failToRetrieveActiveScheduleIdOnMissingExtra() {
        val intent = createIntent()

        try {
            intent.requireActiveScheduleId(extra)
            fail("Unexpected result received")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message, equalTo("Expected active schedule ID [$extra] but none was provided"))
        }
    }

    private val extra = "test-extra"

    private fun createIntent(): Intent {
        return object : Intent() {
            val extras = AtomicReference<Map<String, Any>>(emptyMap())

            override fun putExtra(name: String?, value: Int): Intent = update(name!!, value)
            override fun putExtra(name: String?, value: Long): Intent = update(name!!, value)
            override fun putExtra(name: String?, value: String?): Intent = update(name!!, value!!)

            override fun getIntExtra(name: String?, defaultValue: Int): Int = get(name!!) ?: defaultValue
            override fun getLongExtra(name: String?, defaultValue: Long): Long = get(name!!) ?: defaultValue
            override fun getStringExtra(name: String?): String? = get(name!!)

            private fun update(extra: String, value: Any): Intent {
                val entry = mapOf(Pair(extra, value))
                extras.accumulateAndGet(entry) { a, b -> a + b }
                return this
            }

            @Suppress("UNCHECKED_CAST")
            private fun <T> get(extra: String): T? = extras.get()[extra] as T?
        }
    }
}
