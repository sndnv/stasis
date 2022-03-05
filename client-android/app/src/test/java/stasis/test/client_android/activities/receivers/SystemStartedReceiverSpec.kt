package stasis.test.client_android.activities.receivers

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.activities.receivers.SystemStartedReceiver
import stasis.client_android.scheduling.SchedulerService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class SystemStartedReceiverSpec {
    @Test
    fun startSchedulerServiceOnBoot() {
        val intent = slot<Intent>()

        val context = mockk<Context>(relaxed = true)
        every { context.packageName } returns "test"
        every { context.startForegroundService(capture(intent)) } returns null

        val receiver = SystemStartedReceiver()
        receiver.onReceive(
            context,
            Intent(context, SystemStartedReceiver::class.java).apply {
                action = Intent.ACTION_BOOT_COMPLETED
            }
        )

        assertThat(intent.captured.component?.className, equalTo(SchedulerService::class.java.name))
        assertThat(intent.captured.action, equalTo(null))
    }
}
