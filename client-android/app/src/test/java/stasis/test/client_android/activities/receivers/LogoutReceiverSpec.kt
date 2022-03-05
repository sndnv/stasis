package stasis.test.client_android.activities.receivers

import android.content.Context
import android.content.Intent
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import stasis.client_android.activities.receivers.LogoutReceiver
import stasis.client_android.persistence.credentials.CredentialsViewModel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Config.OLDEST_SDK])
class LogoutReceiverSpec {
    @Test
    fun logoutUser() {
        val credentials = mockk<CredentialsViewModel>(relaxUnitFun = true)
        val context = mockk<Context>(relaxed = true)
        val receiver = LogoutReceiver(credentials)

        receiver.onReceive(
            context = context,
            intent = Intent(context, LogoutReceiver::class.java)
        )

        verify(exactly = 1) { credentials.logout(any()) }
    }
}
