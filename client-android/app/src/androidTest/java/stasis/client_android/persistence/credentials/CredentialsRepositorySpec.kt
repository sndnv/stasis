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
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.utils.Reference
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.mocks.Generators
import stasis.client_android.mocks.MockBackupTracker
import stasis.client_android.mocks.MockCommandProcessor
import stasis.client_android.mocks.MockCredentialsManagementBridge
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
    fun supportVerifyingUserPassword() {
        withSharedPreferences { preferences ->
            val passwordVerificationResult = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(
                    verifyUserPassword = { _ ->
                        passwordVerificationResult.set(true)
                        true
                    }
                )
            )

            repo.verifyUserPassword(password = "password") { result ->
                callbackSuccessful.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(passwordVerificationResult.get(), equalTo(true))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notVerifyUserPasswordWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val passwordVerificationResult = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            repo.verifyUserPassword(password = "password") { result ->
                passwordVerificationResult.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(passwordVerificationResult.get(), equalTo(false))
                }
            }
        }
    }

    @Test
    fun supportUpdatingUserCredentials() {
        withSharedPreferences { preferences ->
            val credentialsUpdated = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(
                    updateUserCredentials = { _, _, _ ->
                        credentialsUpdated.set(true)
                        Success(Fixtures.Secrets.UserPassword.toAuthenticationPassword())
                    }
                )
            )

            repo.updateUserCredentials(
                api = MockServerApiEndpointClient(),
                currentPassword = "current-password",
                newPassword = "new-password",
                newSalt = null
            ) { result ->
                callbackSuccessful.set(result.isSuccess)
            }

            runBlocking {
                eventually {
                    assertThat(credentialsUpdated.get(), equalTo(true))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notUpdateUserCredentialsWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val updateResult = AtomicReference<Try<Unit>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            repo.updateUserCredentials(
                api = MockServerApiEndpointClient(),
                currentPassword = "current-password",
                newPassword = "new-password",
                newSalt = null
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

    @Test
    fun supportPushingDeviceSecret() {
        withSharedPreferences { preferences ->
            val pushedSecret = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(
                    pushDeviceSecret = { _, _ ->
                        pushedSecret.set(true)
                        Success(Unit)
                    }
                )
            )

            repo.pushDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "password",
                remotePassword = null,
            ) { result ->
                callbackSuccessful.set(result.isSuccess)
            }

            runBlocking {
                eventually {
                    assertThat(pushedSecret.get(), equalTo(true))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notPushDeviceSecretWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val pushResult = AtomicReference<Try<Unit>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            repo.pushDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "password",
                remotePassword = null,
            ) { result ->
                pushResult.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(
                        pushResult.get()?.failure()?.message ?: "<missing>",
                        equalTo("Client not configured")
                    )
                }
            }
        }
    }

    @Test
    fun supportPullingDeviceSecret() {
        withSharedPreferences { preferences ->
            val pulledSecret = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(
                    pullDeviceSecret = { _, _ ->
                        pulledSecret.set(true)
                        Success(Fixtures.Secrets.Default)
                    }
                )
            )

            repo.pullDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "password",
                remotePassword = null,
            ) { result ->
                callbackSuccessful.set(result.isSuccess)
            }

            runBlocking {
                eventually {
                    assertThat(pulledSecret.get(), equalTo(true))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notPullDeviceSecretWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val pullResult = AtomicReference<Try<Unit>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            repo.pullDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "password",
                remotePassword = null,
            ) { result ->
                pullResult.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(
                        pullResult.get()?.failure()?.message ?: "<missing>",
                        equalTo("Client not configured")
                    )
                }
            }
        }
    }

    @Test
    fun supportReEncryptingDeviceSecret() {
        withSharedPreferences { preferences ->
            val reEncryptedSecret = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(
                    reEncryptDeviceSecret = { _, _ ->
                        reEncryptedSecret.set(true)
                        Success(Unit)
                    }
                )
            )

            repo.reEncryptDeviceSecret(
                currentPassword = "password",
                oldPassword = "other-password",
            ) { result ->
                callbackSuccessful.set(result.isSuccess)
            }

            runBlocking {
                eventually {
                    assertThat(reEncryptedSecret.get(), equalTo(true))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notReEncryptDeviceSecretWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val reEncryptionResult = AtomicReference<Try<Unit>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            repo.reEncryptDeviceSecret(
                currentPassword = "password",
                oldPassword = "other-password",
            ) { result ->
                reEncryptionResult.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(
                        reEncryptionResult.get()?.failure()?.message ?: "<missing>",
                        equalTo("Client not configured")
                    )
                }
            }
        }
    }

    @Test
    fun supportCheckingIfRemoteDeviceSecretExists() {
        withSharedPreferences { preferences ->
            val callbackSuccessful = AtomicBoolean(false)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory()
            )

            val api = MockServerApiEndpointClient()

            repo.remoteDeviceSecretExists(
                api = api,
            ) { result ->
                callbackSuccessful.set(result.isSuccess)
            }

            runBlocking {
                eventually {
                    assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(1))
                    assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
                    assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
                    assertThat(callbackSuccessful.get(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun notCheckIfRemoteDeviceSecretExistsWhenNotConfigured() {
        withSharedPreferences { preferences ->
            val checkResult = AtomicReference<Try<Boolean>?>(null)

            val repo = CredentialsRepository(
                configPreferences = preferences,
                credentialsPreferences = preferences,
                contextFactory = createContextFactory(provideConfig = false)
            )

            val api = MockServerApiEndpointClient()

            repo.remoteDeviceSecretExists(api = api) { result ->
                assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists], equalTo(0))
                assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled], equalTo(0))
                assertThat(api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed], equalTo(0))
                checkResult.set(result)
            }

            runBlocking {
                eventually {
                    assertThat(
                        checkResult.get()?.failure()?.message ?: "<missing>",
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
        verifyUserPassword: (CharArray) -> Boolean = { _ -> false },
        updateUserCredentials: (CharArray, CharArray, String?) -> Try<UserAuthenticationPassword> = { _, _, _ ->
            Success(
                Fixtures.Secrets.UserPassword.toAuthenticationPassword()
            )
        },
        storeDeviceSecret: suspend (ByteString, CharArray) -> Try<DeviceSecret> = { _, _ -> Success(Fixtures.Secrets.Default) },
        pushDeviceSecret: suspend (ServerApiEndpointClient, CharArray) -> Try<Unit> = { _, _ -> Success(Unit) },
        pullDeviceSecret: suspend (ServerApiEndpointClient, CharArray) -> Try<DeviceSecret> = { _, _ -> Success(Fixtures.Secrets.Default) },
        reEncryptDeviceSecret: suspend (CharArray, CharArray) -> Try<Unit> = { _, _ -> Success(Unit) }
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
                                bridge = object : MockCredentialsManagementBridge(
                                    deviceSecret = Fixtures.Secrets.Default,
                                    authenticationPassword = Fixtures.Secrets.UserPassword.toAuthenticationPassword()
                                ) {
                                    override fun verifyUserPassword(userPassword: CharArray): Boolean =
                                        verifyUserPassword(userPassword)

                                    override suspend fun updateUserCredentials(
                                        api: ServerApiEndpointClient,
                                        currentUserPassword: CharArray,
                                        newUserPassword: CharArray,
                                        newUserSalt: String?
                                    ): Try<UserAuthenticationPassword> =
                                        updateUserCredentials(currentUserPassword, newUserPassword, newUserSalt)

                                    override suspend fun storeDeviceSecret(
                                        secret: ByteString,
                                        userPassword: CharArray
                                    ): Try<DeviceSecret> = storeDeviceSecret(secret, userPassword)

                                    override suspend fun pushDeviceSecret(
                                        api: ServerApiEndpointClient,
                                        userPassword: CharArray,
                                        remotePassword: CharArray?
                                    ): Try<Unit> = pushDeviceSecret(api, userPassword)

                                    override suspend fun pullDeviceSecret(
                                        api: ServerApiEndpointClient,
                                        userPassword: CharArray,
                                        remotePassword: CharArray?
                                    ): Try<DeviceSecret> = pullDeviceSecret(api, userPassword)

                                    override suspend fun reEncryptDeviceSecret(
                                        currentUserPassword: CharArray,
                                        oldUserPassword: CharArray
                                    ): Try<Unit> = reEncryptDeviceSecret(currentUserPassword, oldUserPassword)
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
}
