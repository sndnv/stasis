package stasis.client_android.persistence.credentials

import android.app.Application
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import stasis.client_android.Fixtures
import stasis.client_android.await
import stasis.client_android.eventually
import stasis.client_android.failure
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.utils.Reference
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.mocks.Generators
import stasis.client_android.mocks.MockBackupTracker
import stasis.client_android.mocks.MockOperationExecutor
import stasis.client_android.mocks.MockRecoveryTracker
import stasis.client_android.mocks.MockSearch
import stasis.client_android.mocks.MockServerApiEndpointClient
import stasis.client_android.mocks.MockServerCoreEndpointClient
import stasis.client_android.mocks.MockServerMonitor
import stasis.client_android.mocks.MockServerTracker
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.tracking.TrackerViews
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CredentialsRepositorySpec {
    @Test
    fun supportLoginAndLogout() {
        withSharedPreferences { preferences ->
            val loginResult = AtomicReference<Try<Unit>?>(null)
            val logoutCompleted = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory()
            )

            assertThat(repo.available, equalTo(false))
            assertThat(repo.user.await().failure().message, equalTo("No access token found"))
            assertThat(repo.device.await().failure().message, equalTo("No access token found"))

            repo.login(username = "user", password = "password") { loginResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(loginResult.get()?.isSuccess ?: false, equalTo(true))

                    assertThat(repo.available, equalTo(true))
                    assertThat(repo.user.await().isSuccess, equalTo(true))
                    assertThat(repo.device.await().isSuccess, equalTo(true))
                }
            }

            repo.logout {
                logoutCompleted.set(true)
            }

            runBlocking {
                eventually {
                    assertThat(repo.available, equalTo(false))
                    assertThat(
                        repo.user.await().failure().message,
                        equalTo("No access token found")
                    )
                    assertThat(
                        repo.device.await().failure().message,
                        equalTo("No access token found")
                    )
                    assertThat(logoutCompleted.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notLoginWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val loginResult = AtomicReference<Try<Unit>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            assertThat(repo.available, equalTo(false))
            assertThat(repo.user.await().failure().message, equalTo("No access token found"))
            assertThat(repo.device.await().failure().message, equalTo("No access token found"))

            repo.login(username = "user", password = "password") { loginResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(repo.available, equalTo(false))
                    assertThat(
                        repo.user.await().failure().message,
                        equalTo("No access token found")
                    )
                    assertThat(
                        repo.device.await().failure().message,
                        equalTo("No access token found")
                    )

                    assertThat(
                        loginResult.get()?.failure()?.message ?: "<missing>",
                        equalTo("Client not configured")
                    )
                }
            }
        }
    }

    @Test
    fun supportUpdatingDeviceSecret() {
        withSharedPreferences { preferences ->
            val storedSecret = AtomicReference<ByteString>(null)
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(
                    storeDeviceSecret = { stored, _ ->
                        storedSecret.set(stored)
                        Success(Fixtures.Secrets.Default)
                    }
                )
            )

            val otherSecret = "other-secret".toByteArray().toByteString()

            repo.updateDeviceSecret(
                password = "password",
                secret = otherSecret
            ) { result ->
                callbackSuccessful.set(result.isSuccess)
            }

            runBlocking {
                eventually {
                    assertThat(storedSecret.get(), equalTo(otherSecret))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notUpdateDeviceSecretWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val updateResult = AtomicReference<Try<Unit>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            repo.updateDeviceSecret(
                password = "password",
                secret = "other-secret".toByteArray().toByteString()
            ) { result ->
                updateResult.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(
                        updateResult.get()?.failure()?.message ?: "<missing>",
                        equalTo("Client not configured")
                    )
                }
            }
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val tokenResponse = AccessTokenResponse(
        access_token = Generators.generateJwt(sub = "test-subject"),
        refresh_token = null,
        expires_in = 42,
        scope = null
    )

    private fun withSharedPreferences(f: (SharedPreferences) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val preferences = ConfigRepository.getPreferences(application)

        try {
            preferences.edit().clear().commit()
            f(preferences)
        } finally {
            preferences.edit().clear().commit()
        }
    }

    private fun createContextFactory(
        withResponse: Try<AccessTokenResponse> = Success(tokenResponse),
        provideConfig: Boolean = true,
        storeDeviceSecret: suspend (ByteString, CharArray) -> Try<DeviceSecret> = { _, _ -> Success(Fixtures.Secrets.Default) }
    ): ProviderContext.Factory =
        object : ProviderContext.Factory {
            override fun getOrCreate(preferences: SharedPreferences): Reference<ProviderContext> =
                Reference.Singleton(
                    retrieveConfig = {
                        if (provideConfig) 42
                        else null
                    },
                    create = {
                        ProviderContext(
                            core = MockServerCoreEndpointClient(),
                            api = MockServerApiEndpointClient(),
                            search = MockSearch(),
                            executor = MockOperationExecutor(),
                            trackers = TrackerViews(
                                backup = MockBackupTracker(),
                                recovery = MockRecoveryTracker(),
                                server = MockServerTracker()
                            ),
                            credentials = CredentialsProvider(
                                config = CredentialsProvider.Config(
                                    coreScope = "core",
                                    apiScope = "api",
                                    expirationTolerance = Duration.ZERO
                                ),
                                oAuthClient = object : OAuthClient {
                                    override suspend fun token(
                                        scope: String?,
                                        parameters: OAuthClient.GrantParameters,
                                    ): Try<AccessTokenResponse> = withResponse
                                },
                                initDeviceSecret = { Fixtures.Secrets.Default },
                                loadDeviceSecret = { Success(Fixtures.Secrets.Default) },
                                storeDeviceSecret = storeDeviceSecret,
                                coroutineScope = CoroutineScope(Dispatchers.IO),
                                getAuthenticationPassword = { Fixtures.Secrets.UserPassword.toHashedAuthenticationPassword() }
                            ),
                            monitor = MockServerMonitor()
                        )
                    },
                    destroy = {}
                )
        }
}
