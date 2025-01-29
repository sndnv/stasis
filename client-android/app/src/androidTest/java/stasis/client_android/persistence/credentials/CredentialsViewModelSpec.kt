package stasis.client_android.persistence.credentials

import android.app.Application
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import stasis.client_android.lib.ops.search.Search
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.utils.Reference
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.mocks.MockBackupTracker
import stasis.client_android.mocks.MockCommandProcessor
import stasis.client_android.mocks.MockCredentialsManagementBridge
import stasis.client_android.mocks.MockOAuthClient
import stasis.client_android.mocks.MockOperationExecutor
import stasis.client_android.mocks.MockRecoveryTracker
import stasis.client_android.mocks.MockServerApiEndpointClient
import stasis.client_android.mocks.MockServerCoreEndpointClient
import stasis.client_android.mocks.MockServerMonitor
import stasis.client_android.mocks.MockServerTracker
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.tracking.TrackerViews
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class CredentialsViewModelSpec {
    @Test
    fun supportLoginAndLogout() {
        withModel { model ->
            val loginResult = AtomicReference<Try<Unit>?>(null)
            val logoutCompleted = AtomicBoolean(false)

            assertThat(model.available, equalTo(false))
            assertThat(model.user.await().failure().message, equalTo("No access token found"))
            assertThat(model.device.await().failure().message, equalTo("No access token found"))

            model.login(username = "user", password = "password") { loginResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(loginResult.get()?.isSuccess ?: false, equalTo(true))

                    assertThat(model.available, equalTo(true))
                    assertThat(model.user.await().isSuccess, equalTo(true))
                    assertThat(model.device.await().isSuccess, equalTo(true))
                }
            }

            model.logout {
                logoutCompleted.set(true)
            }

            runBlocking {
                eventually {
                    assertThat(
                        model.available,
                        equalTo(false)
                    )

                    assertThat(
                        logoutCompleted.get(),
                        equalTo(true)
                    )

                    assertThat(
                        model.user.await().failure().message,
                        equalTo("No access token found")
                    )

                    assertThat(
                        model.device.await().failure().message,
                        equalTo("No access token found")
                    )
                }
            }
        }
    }

    @Test
    fun supportVerifyingUserPassword() {
        withModel { model ->
            val updateResult = AtomicBoolean(false)
            model.verifyUserPassword(password = "password") { updateResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(updateResult.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun supportUpdatingUserCredentials() {
        withModel { model ->
            val updateResult = AtomicReference<Try<Unit>?>(null)

            model.updateUserCredentials(
                api = MockServerApiEndpointClient(),
                currentPassword = "current-password",
                newPassword = "new-password",
                newSalt = null
            ) { updateResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(updateResult.get()?.isSuccess ?: false, equalTo(true))
                }
            }
        }
    }

    @Test
    fun supportUpdatingDeviceSecret() {
        withModel { model ->
            val updateResult = AtomicReference<Try<Unit>?>(null)

            model.updateDeviceSecret(
                password = "password",
                secret = "other-secret".toByteArray().toByteString()
            ) { updateResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(updateResult.get()?.isSuccess ?: false, equalTo(true))
                }
            }
        }
    }

    @Test
    fun supportPushingDeviceSecret() {
        withModel { model ->
            val pushResult = AtomicReference<Try<Unit>?>(null)

            model.pushDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "password",
                remotePassword = null,
            ) { pushResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(pushResult.get()?.isSuccess ?: false, equalTo(true))
                }
            }
        }
    }

    @Test
    fun supportPullingDeviceSecret() {
        withModel { model ->
            val pullResult = AtomicReference<Try<Unit>?>(null)

            model.pullDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "password",
                remotePassword = null,
            ) { pullResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(pullResult.get()?.isSuccess ?: false, equalTo(true))
                }
            }
        }
    }

    @Test
    fun supportReEncryptingDeviceSecret() {
        withModel { model ->
            val reEncryptionResult = AtomicReference<Try<Unit>?>(null)

            model.reEncryptDeviceSecret(
                currentPassword = "password",
                oldPassword = "other-password",
            ) { reEncryptionResult.set(it) }

            runBlocking {
                eventually {
                    assertThat(reEncryptionResult.get()?.isSuccess ?: false, equalTo(true))
                }
            }
        }
    }

    @Test
    fun supportCheckingIfRemoteDeviceSecretExists() {
        withModel { model ->
            val result = AtomicReference<Try<Boolean>?>(null)

            model.remoteDeviceSecretExists(
                api = MockServerApiEndpointClient()
            ) { result.set(it) }

            runBlocking {
                eventually {
                    assertThat(result.get()?.isSuccess ?: false, equalTo(true))
                }
            }
        }
    }

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private fun withModel(f: (CredentialsViewModel) -> Unit) {
        val contextFactory = object : ProviderContext.Factory {
            override fun getOrCreate(preferences: SharedPreferences): Reference<ProviderContext> =
                Reference.Singleton(
                    retrieveConfig = { },
                    create = {
                        ProviderContext(
                            core = MockServerCoreEndpointClient(),
                            api = MockServerApiEndpointClient(),
                            search = object : Search {
                                override suspend fun search(
                                    query: Regex,
                                    until: Instant?,
                                ): Try<Search.Result> =
                                    Success(Search.Result(definitions = emptyMap()))
                            },
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
                                oAuthClient = MockOAuthClient(),
                                bridge = object : MockCredentialsManagementBridge(
                                    deviceSecret = Fixtures.Secrets.Default,
                                    authenticationPassword = Fixtures.Secrets.UserPassword.toAuthenticationPassword()
                                ) {
                                    override fun verifyUserPassword(userPassword: CharArray): Boolean =
                                        true
                                },
                                coroutineScope = CoroutineScope(Dispatchers.IO)
                            ),
                            monitor = MockServerMonitor(),
                            commandProcessor = MockCommandProcessor(),
                            secretsConfig = Fixtures.Secrets.DefaultConfig
                        )
                    },
                    destroy = {}
                )
        }

        val application = ApplicationProvider.getApplicationContext<Application>()
        val model = CredentialsViewModel(contextFactory, application)
        val preferences = ConfigRepository.getPreferences(application)

        try {
            preferences
                .edit()
                .clear()
                .putString(
                    ConfigRepository.Companion.Keys.Authentication.TokenEndpoint,
                    "test-endpoint"
                )
                .putString(
                    ConfigRepository.Companion.Keys.Authentication.ClientId,
                    "test-client"
                )
                .putString(
                    ConfigRepository.Companion.Keys.Authentication.ClientSecret,
                    "test-client-secret"
                )
                .putString(
                    ConfigRepository.Companion.Keys.Authentication.ScopeApi,
                    "test-scope-api"
                )
                .putString(
                    ConfigRepository.Companion.Keys.Authentication.ScopeCore,
                    "test-scope-core"
                )
                .commit()
            f(model)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
