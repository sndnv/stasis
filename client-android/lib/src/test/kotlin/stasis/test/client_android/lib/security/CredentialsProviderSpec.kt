package stasis.test.client_android.lib.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.jose4j.jwk.RsaJwkGenerator
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.security.OAuthClient
import stasis.client_android.lib.security.exceptions.MissingDeviceSecret
import stasis.client_android.lib.security.exceptions.TokenExpired
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.test.client_android.lib.Fixtures
import stasis.test.client_android.lib.eventually
import stasis.test.client_android.lib.mocks.MockCredentialsManagementBridge
import stasis.test.client_android.lib.mocks.MockServerApiEndpointClient
import java.time.Duration
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CredentialsProviderSpec : WordSpec({
    "A CredentialsProvider" should {
        val config = CredentialsProvider.Config(
            coreScope = "core-scope",
            apiScope = "api-scope",
            expirationTolerance = Duration.ofMillis(100)
        )

        val response = AccessTokenResponse(
            access_token = "test-token",
            refresh_token = "refresh-token",
            expires_in = 1,
            scope = null
        )

        val rsaKey = RsaJwkGenerator.generateJwk(2048).apply {
            keyId = "test-key"
        }

        val secret = DeviceSecret(
            user = UUID.randomUUID(),
            device = UUID.randomUUID(),
            secret = "test-secret".toByteArray().toByteString(),
            target = Fixtures.Secrets.DefaultConfig
        )

        val hashedPassword = UserAuthenticationPassword.Hashed(
            user = UUID.randomUUID(),
            hashedPassword = "test-password".toByteArray().toByteString()
        )

        fun createJwt(expiresIn: Float = 1.0f): String {
            val claims = JwtClaims().apply {
                subject = "test-subject"
                setIssuedAtToNow()
                setGeneratedJwtId()
                setExpirationTimeMinutesInTheFuture(expiresIn)
            }

            val jws = JsonWebSignature().apply {
                payload = claims.toJson()
                key = rsaKey.privateKey
                keyIdHeaderValue = rsaKey.keyId
                algorithmHeaderValue = AlgorithmIdentifiers.RSA_USING_SHA256
            }

            return jws.compactSerialization
        }

        "support initializing with existing token responses (valid)" {
            val requested = AtomicInteger(0)

            val coreUpdates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())
            val apiUpdates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val coreToken = response.copy(access_token = createJwt(), refresh_token = null)
            val apiToken = response.copy(access_token = createJwt(), refresh_token = null)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            provider
                .setOnCoreTokenUpdatedHandler(this) { coreUpdates.add(it) }
                .setOnApiTokenUpdatedHandler(this) { apiUpdates.add(it) }

            provider.init(
                coreToken = coreToken,
                apiToken = apiToken,
                plaintextDeviceSecret = secret.secret,
                digestedUserPassword = "test-password"
            )

            eventually {
                requested.get() shouldBe (0)
                coreUpdates.size shouldBe (1)
                apiUpdates.size shouldBe (1)

                coreUpdates.map { it.map { token -> token.copy(expires_in = 1) } } shouldBe (listOf(
                    Success(coreToken)
                ))
                apiUpdates.map { it.map { token -> token.copy(expires_in = 1) } } shouldBe (listOf(
                    Success(apiToken)
                ))

                provider.core().map { token -> token.copy(expires_in = 1) } shouldBe (Success(
                    coreToken
                ))
                provider.api().map { token -> token.copy(expires_in = 1) } shouldBe (Success(apiToken))

                provider.deviceSecret shouldBe (Success(secret))
            }

            provider.logout()
        }

        "support initializing with existing token responses (expired / with refresh tokens)" {
            val requested = AtomicInteger(0)

            val coreUpdates =
                Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())
            val apiUpdates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val coreToken = response.copy(access_token = createJwt(expiresIn = -1.0f))
            val apiToken = response.copy(access_token = createJwt(expiresIn = -1.0f))

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            provider
                .setOnCoreTokenUpdatedHandler(this) { coreUpdates.add(it) }
                .setOnApiTokenUpdatedHandler(this) { apiUpdates.add(it) }

            provider.init(
                coreToken = coreToken,
                apiToken = apiToken,
                plaintextDeviceSecret = secret.secret,
                digestedUserPassword = "test-password"
            )

            eventually {
                requested.get() shouldBe (2)
                coreUpdates.size shouldBe (1)
                apiUpdates.size shouldBe (1)

                coreUpdates shouldBe (listOf(Success(response)))
                apiUpdates shouldBe (listOf(Success(response)))

                provider.core() shouldBe (Success(response))
                provider.api() shouldBe (Success(response))
                provider.deviceSecret shouldBe (Success(secret))
            }

            provider.logout()
        }

        "support initializing with existing token responses (expired / without refresh tokens)" {
            val requested = AtomicInteger(0)

            val coreUpdates =
                Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())
            val apiUpdates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val coreToken =
                response.copy(access_token = createJwt(expiresIn = -1.0f), refresh_token = null)
            val apiToken =
                response.copy(access_token = createJwt(expiresIn = -1.0f), refresh_token = null)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            provider
                .setOnCoreTokenUpdatedHandler(this) { coreUpdates.add(it) }
                .setOnApiTokenUpdatedHandler(this) { apiUpdates.add(it) }

            provider.init(
                coreToken = coreToken,
                apiToken = apiToken,
                plaintextDeviceSecret = secret.secret,
                digestedUserPassword = "test-password"
            )

            eventually {
                requested.get() shouldBe (0)
                coreUpdates.size shouldBe (1)
                apiUpdates.size shouldBe (1)

                shouldThrow<TokenExpired> { coreUpdates[0].get() }
                shouldThrow<TokenExpired> { apiUpdates[0].get() }

                shouldThrow<TokenExpired> { provider.core().get() }
                shouldThrow<TokenExpired> { provider.api().get() }
                shouldThrow<MissingDeviceSecret> { provider.deviceSecret.get() }
            }

            provider.logout()
        }

        "support initializing with existing refresh tokens" {
            val requested = AtomicInteger(0)

            val coreUpdates =
                Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())
            val apiUpdates = Collections.synchronizedList(mutableListOf<Try<AccessTokenResponse>>())

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            provider
                .setOnCoreTokenUpdatedHandler(this) { coreUpdates.add(it) }
                .setOnApiTokenUpdatedHandler(this) { apiUpdates.add(it) }

            provider.init(
                apiRefreshToken = "api-token",
                plaintextDeviceSecret = secret.secret,
                digestedUserPassword = "test-password"
            )

            eventually {
                requested.get() shouldBe (2)
                coreUpdates.size shouldBe (1)
                apiUpdates.size shouldBe (1)

                coreUpdates shouldBe (listOf(
                    Success(response)
                ))
                apiUpdates shouldBe (listOf(
                    Success(response)
                ))

                provider.core() shouldBe (Success(response))
                provider.api() shouldBe (Success(response))
                provider.deviceSecret shouldBe (Success(secret))
            }

            provider.logout()
        }

        "support logging in with a username and password" {
            val requested = AtomicInteger(0)
            val initCompleted = AtomicBoolean(false)

            val coreUpdates = Collections.synchronizedList(
                mutableListOf<Try<AccessTokenResponse>>()
            )

            val apiUpdates = Collections.synchronizedList(
                mutableListOf<Try<AccessTokenResponse>>()
            )

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            provider
                .setOnCoreTokenUpdatedHandler(this) { coreUpdates.add(it) }
                .setOnApiTokenUpdatedHandler(this) { apiUpdates.add(it) }

            provider.login(username = "user", password = "password") {
                initCompleted.set(it.isSuccess)
            }

            eventually {
                requested.get() shouldBe (2)
                initCompleted.get() shouldBe (true)
                coreUpdates.size shouldBe (1)
                apiUpdates.size shouldBe (1)

                coreUpdates shouldBe (listOf(
                    Success(response)
                ))
                apiUpdates shouldBe (listOf(
                    Success(response)
                ))

                provider.core() shouldBe (Success(response))
                provider.api() shouldBe (Success(response))
                provider.deviceSecret shouldBe (Success(secret))
            }

            provider.logout()
        }

        "fail to log in if the device's secret cannot be decrypted" {
            val requested = AtomicInteger(0)
            val initCompleted = AtomicBoolean(false)

            val coreUpdates = Collections.synchronizedList(
                mutableListOf<Try<AccessTokenResponse>>()
            )

            val apiUpdates = Collections.synchronizedList(
                mutableListOf<Try<AccessTokenResponse>>()
            )

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    requested.incrementAndGet()
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override suspend fun loadDeviceSecret(userPassword: CharArray): Try<DeviceSecret> =
                        Failure(RuntimeException("Test failure"))
                },
                coroutineScope = testScope
            )

            provider
                .setOnCoreTokenUpdatedHandler(this) { coreUpdates.add(it) }
                .setOnApiTokenUpdatedHandler(this) { apiUpdates.add(it) }

            provider.login(username = "user", password = "password") {
                initCompleted.set(it.isFailure)
            }

            eventually {
                requested.get() shouldBe (0)
                initCompleted.get() shouldBe (true)
                coreUpdates.size shouldBe (0)
                apiUpdates.size shouldBe (0)

                provider.deviceSecret.failed()
                    .map { it.message } shouldBe (Success("Test failure"))
            }
        }

        "support verifying user passwords" {
            val currentPassword = "test-password"

            val verificationResult = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override fun verifyUserPassword(userPassword: CharArray): Boolean {
                        return String(userPassword) == currentPassword
                    }
                },
                coroutineScope = testScope
            )

            verificationResult.get() shouldBe (false)
            callbackSuccessful.get() shouldBe (false)
            provider.verifyUserPassword(password = currentPassword) { result ->
                verificationResult.set(result)
                callbackSuccessful.set(true)
            }

            eventually {
                verificationResult.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
            }
        }

        "support updating user credentials" {
            val credentialsUpdated = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val mockApi = MockServerApiEndpointClient()

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            credentialsUpdated.get() shouldBe (false)
            callbackSuccessful.get() shouldBe (false)

            mockApi.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (0)

            provider.updateUserCredentials(
                api = mockApi,
                currentPassword = "current-password",
                newPassword = "new-password",
                newSalt = "test-salt"
            ) { result ->
                credentialsUpdated.set(result.isSuccess)
                callbackSuccessful.set(true)
            }

            eventually {
                credentialsUpdated.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
                mockApi.statistics[MockServerApiEndpointClient.Statistic.UserPasswordUpdated] shouldBe (1)
            }
        }

        "support updating the device's secret" {
            val secretUpdated = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val otherSecret = DeviceSecret(
                user = UUID.randomUUID(),
                device = UUID.randomUUID(),
                secret = "other-test-secret".toByteArray().toByteString(),
                target = Fixtures.Secrets.DefaultConfig
            )

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override suspend fun storeDeviceSecret(
                        secret: ByteString,
                        userPassword: CharArray
                    ): Try<DeviceSecret> {
                        secretUpdated.set(true)
                        return Success(otherSecret)
                    }
                },
                coroutineScope = testScope
            )


            provider.updateDeviceSecret(
                plaintextDeviceSecret = otherSecret.secret,
                password = "test-password"
            ) {
                callbackSuccessful.set(it.isSuccess)
            }

            eventually {
                secretUpdated.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
                provider.deviceSecret shouldBe (Success(otherSecret))
            }
        }

        "support pushing the device's secret" {
            val secretPushed = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override suspend fun pushDeviceSecret(
                        api: ServerApiEndpointClient,
                        userPassword: CharArray,
                        remotePassword: CharArray?
                    ): Try<Unit> {
                        secretPushed.set(true)
                        return Success(Unit)
                    }
                },
                coroutineScope = testScope
            )


            provider.pushDeviceSecret(
                api = MockServerApiEndpointClient(),

                password = "test-password",
                remotePassword = null
            ) {
                callbackSuccessful.set(it.isSuccess)
            }

            eventually {
                secretPushed.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
            }
        }

        "support pulling the device's secret" {
            val secretPulled = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override suspend fun pullDeviceSecret(
                        api: ServerApiEndpointClient,
                        userPassword: CharArray,
                        remotePassword: CharArray?
                    ): Try<DeviceSecret> {
                        secretPulled.set(true)
                        return Success(secret)
                    }
                },
                coroutineScope = testScope
            )

            eventually {
                secretPulled.get() shouldBe (false)
                callbackSuccessful.get() shouldBe (false)
                shouldThrow<MissingDeviceSecret> { provider.deviceSecret.get() }
            }

            provider.pullDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "test-password",
                remotePassword = null
            ) {
                callbackSuccessful.set(it.isSuccess)
            }

            eventually {
                secretPulled.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
                provider.deviceSecret shouldBe (Success(secret))
            }
        }

        "not update the device's secret if the pull failed" {
            val secretPulled = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override suspend fun pullDeviceSecret(
                        api: ServerApiEndpointClient,
                        userPassword: CharArray,
                        remotePassword: CharArray?
                    ): Try<DeviceSecret> {
                        secretPulled.set(true)
                        return Failure(RuntimeException("Test failure"))
                    }
                },
                coroutineScope = testScope
            )

            eventually {
                secretPulled.get() shouldBe (false)
                callbackSuccessful.get() shouldBe (false)
                shouldThrow<MissingDeviceSecret> { provider.deviceSecret.get() }
            }

            provider.pullDeviceSecret(
                api = MockServerApiEndpointClient(),
                password = "test-password",
                remotePassword = null
            ) {
                callbackSuccessful.set(it.isFailure)
            }

            eventually {
                secretPulled.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
                shouldThrow<MissingDeviceSecret> { provider.deviceSecret.get() }
            }
        }

        "support re-encrypting the device's secret" {
            val secretReEncrypted = AtomicBoolean(false)
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = object : MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ) {
                    override suspend fun reEncryptDeviceSecret(
                        currentUserPassword: CharArray,
                        oldUserPassword: CharArray
                    ): Try<Unit> {
                        secretReEncrypted.set(true)
                        return Success(Unit)
                    }
                },
                coroutineScope = testScope
            )

            eventually {
                secretReEncrypted.get() shouldBe (false)
                callbackSuccessful.get() shouldBe (false)
            }

            provider.reEncryptDeviceSecret(
                currentPassword = "test-password",
                oldPassword = "other-password"
            ) {
                callbackSuccessful.set(it.isSuccess)
            }

            eventually {
                secretReEncrypted.get() shouldBe (true)
                callbackSuccessful.get() shouldBe (true)
            }
        }

        "support checking if the device's remote secret exists" {
            val callbackSuccessful = AtomicBoolean(false)

            val client = object : OAuthClient {
                override suspend fun token(
                    scope: String?,
                    parameters: OAuthClient.GrantParameters,
                ): Try<AccessTokenResponse> {
                    return Success(response)
                }
            }

            val provider = CredentialsProvider(
                config = config,
                oAuthClient = client,
                bridge = MockCredentialsManagementBridge(
                    deviceSecret = secret,
                    authenticationPassword = hashedPassword
                ),
                coroutineScope = testScope
            )

            val api = MockServerApiEndpointClient()

            provider.remoteDeviceSecretExists(
                api = api,
            ) {
                callbackSuccessful.set(it.isSuccess)
            }

            eventually {
                api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyExists] shouldBe (1)
                api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPulled] shouldBe (0)
                api.statistics[MockServerApiEndpointClient.Statistic.DeviceKeyPushed] shouldBe (0)
                callbackSuccessful.get() shouldBe (true)
            }
        }
    }
})
