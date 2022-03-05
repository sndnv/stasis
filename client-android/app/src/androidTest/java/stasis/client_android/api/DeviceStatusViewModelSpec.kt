package stasis.client_android.api

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.Fixtures
import stasis.client_android.await
import stasis.client_android.eventually
import stasis.client_android.lib.model.server.devices.Device
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.utils.Cache
import stasis.client_android.lib.utils.Reference
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.mocks.*
import stasis.client_android.providers.ProviderContext
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class DeviceStatusViewModelSpec {
    @Test
    fun provideDeviceStatus() {
        val mockApiClient = MockServerApiEndpointClient()

        val deviceCache = Cache.Refreshing<Int, Device>(
            underlying = Cache.Map(),
            interval = Duration.ofMillis(100),
            scope = CoroutineScope(Dispatchers.IO)
        )

        val contextFactory = object : ProviderContext.Factory {
            override fun getOrCreate(preferences: SharedPreferences): Reference<ProviderContext> =
                Reference.Singleton(
                    retrieveConfig = { },
                    create = {
                        ProviderContext(
                            core = MockServerCoreEndpointClient(),
                            api = mockApiClient,
                            search = MockSearch(),
                            executor = MockOperationExecutor(),
                            tracker = MockTrackerView(),
                            credentials = CredentialsProvider(
                                config = CredentialsProvider.Config(
                                    coreScope = "core",
                                    apiScope = "api",
                                    expirationTolerance = Duration.ZERO
                                ),
                                oAuthClient = MockOAuthClient(),
                                initDeviceSecret = { Fixtures.Secrets.Default },
                                loadDeviceSecret = { Success(Fixtures.Secrets.Default) },
                                storeDeviceSecret = { _, _ -> Success(Fixtures.Secrets.Default) },
                                coroutineScope = CoroutineScope(Dispatchers.IO),
                                getAuthenticationPassword = { Fixtures.Secrets.UserPassword.toHashedAuthenticationPassword() }
                            ),
                            monitor = MockServerMonitor()
                        )
                    },
                    destroy = {}
                )
        }

        val model = DeviceStatusViewModel(
            application = ApplicationProvider.getApplicationContext(),
            providerContextFactory = contextFactory,
            deviceCache = deviceCache
        )

        runBlocking {
            eventually {
                model.device.await()

                assertThat(
                    mockApiClient.statistics[MockServerApiEndpointClient.Statistic.DeviceRetrieved],
                    equalTo(3)
                )
            }
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()
}
