package stasis.client_android.lib.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.JwtContext
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.server.api.requests.ResetUserPassword
import stasis.client_android.lib.security.exceptions.MissingDeviceSecret
import stasis.client_android.lib.security.exceptions.TokenExpired
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

@Suppress("LongParameterList")
class CredentialsProvider(
    private val config: Config,
    private val oAuthClient: OAuthClient,
    private val bridge: CredentialsManagementBridge,
    private val coroutineScope: CoroutineScope,
) {
    private val onCoreTokenUpdatedHandlers: ConcurrentHashMap<Any, (Try<AccessTokenResponse>) -> Unit> =
        ConcurrentHashMap()

    private val onApiTokenUpdatedHandlers: ConcurrentHashMap<Any, (Try<AccessTokenResponse>) -> Unit> =
        ConcurrentHashMap()

    private val latestDeviceSecret: AtomicReference<Try<DeviceSecret>> =
        AtomicReference(Failure(MissingDeviceSecret()))

    private val extractor: JwtConsumer = JwtConsumerBuilder()
        .setSkipSignatureVerification()
        .setRequireExpirationTime()
        .setAllowedClockSkewInSeconds(config.expirationTolerance.seconds.toInt())
        .setSkipDefaultAudienceValidation()
        .build()

    private val coreTokenManager: OAuthTokenManager = OAuthTokenManager(
        oAuthClient = oAuthClient,
        onTokenUpdated = { response ->
            onCoreTokenUpdatedHandlers.values.forEach { handler -> handler(response) }
        },
        expirationTolerance = config.expirationTolerance,
        coroutineScope = coroutineScope
    )

    private val apiTokenManager: OAuthTokenManager = OAuthTokenManager(
        oAuthClient = oAuthClient,
        onTokenUpdated = { response ->
            onApiTokenUpdatedHandlers.values.forEach { handler -> handler(response) }
        },
        expirationTolerance = config.expirationTolerance,
        coroutineScope = coroutineScope
    )

    val core: Try<AccessTokenResponse>
        get() = coreTokenManager.token


    val api: Try<AccessTokenResponse>
        get() = apiTokenManager.token

    val deviceSecret: Try<DeviceSecret>
        get() = latestDeviceSecret.get()

    fun init(
        coreToken: AccessTokenResponse,
        apiToken: AccessTokenResponse,
        plaintextDeviceSecret: ByteString,
        digestedUserPassword: String,
    ) {
        try {
            val coreExpiresIn = extractor.process(coreToken.access_token).expiresIn()
            val apiExpiresIn = extractor.process(apiToken.access_token).expiresIn()

            coreTokenManager.scheduleWithClientCredentials(Success(coreToken.copy(expires_in = coreExpiresIn)))
            apiTokenManager.scheduleWithRefreshToken(Success(apiToken.copy(expires_in = apiExpiresIn)))

            latestDeviceSecret.set(Success(bridge.initDeviceSecret(plaintextDeviceSecret)))
            bridge.initDigestedUserPassword(digestedUserPassword)
        } catch (_: InvalidJwtException) {
            when (val apiRefreshToken = apiToken.refresh_token) {
                null -> {
                    coreTokenManager.scheduleWithClientCredentials(Failure(TokenExpired()))
                    apiTokenManager.scheduleWithRefreshToken(Failure(TokenExpired()))
                    latestDeviceSecret.set(Failure(MissingDeviceSecret()))
                }

                else -> init(
                    apiRefreshToken = apiRefreshToken,
                    plaintextDeviceSecret = plaintextDeviceSecret,
                    digestedUserPassword = digestedUserPassword
                )
            }
        }
    }

    fun init(
        apiRefreshToken: String,
        plaintextDeviceSecret: ByteString,
        digestedUserPassword: String,
    ) {
        coroutineScope.launch {
            val coreTokenResponse = oAuthClient.token(
                scope = config.coreScope,
                parameters = OAuthClient.GrantParameters.ClientCredentials
            )

            val apiTokenResponse = oAuthClient.token(
                scope = config.apiScope,
                parameters = OAuthClient.GrantParameters.RefreshToken(apiRefreshToken)
            )

            coreTokenManager.scheduleWithClientCredentials(coreTokenResponse)
            apiTokenManager.scheduleWithRefreshToken(apiTokenResponse)
            latestDeviceSecret.set(Success(bridge.initDeviceSecret(plaintextDeviceSecret)))
            bridge.initDigestedUserPassword(digestedUserPassword)
        }
    }

    fun login(username: String, password: String, f: (Try<Pair<DeviceSecret, String>>) -> Unit) {
        coroutineScope.launch {
            when (val deviceSecretResult = bridge.loadDeviceSecret(password.toCharArray())) {
                is Success -> {
                    val authenticationPassword =
                        bridge.getAuthenticationPassword(password.toCharArray())

                    val coreTokenResponse = oAuthClient.token(
                        scope = config.coreScope,
                        parameters = OAuthClient.GrantParameters.ClientCredentials
                    )

                    val apiTokenResponse = oAuthClient.token(
                        scope = config.apiScope,
                        parameters = OAuthClient.GrantParameters.ResourceOwnerPasswordCredentials(
                            username = username,
                            password = authenticationPassword.extract()
                        )
                    )

                    val digestedUserPassword = authenticationPassword.digested()

                    coreTokenManager.scheduleWithClientCredentials(coreTokenResponse)
                    apiTokenManager.scheduleWithRefreshToken(apiTokenResponse)
                    latestDeviceSecret.set(deviceSecretResult)
                    bridge.initDigestedUserPassword(digestedUserPassword)

                    f(coreTokenResponse.flatMap { apiTokenResponse }
                        .flatMap { deviceSecretResult.map { Pair(it, digestedUserPassword) } })
                }

                is Failure -> {
                    latestDeviceSecret.set(deviceSecretResult)
                    f(Failure(deviceSecretResult.exception))
                }
            }
        }
    }

    fun logout() {
        coreTokenManager.reset()
        apiTokenManager.reset()
        latestDeviceSecret.set(Failure(MissingDeviceSecret()))
        bridge.initDigestedUserPassword(null)
    }

    fun verifyUserPassword(password: String, f: (Boolean) -> Unit) {
        coroutineScope.launch {
            f(bridge.verifyUserPassword(password.toCharArray()))
        }
    }

    fun updateUserCredentials(
        api: ServerApiEndpointClient,
        currentPassword: String,
        newPassword: String,
        newSalt: String?,
        f: (Try<String>) -> Unit
    ) {
        coroutineScope.launch {
            val result = bridge.updateUserCredentials(
                api = api,
                currentUserPassword = currentPassword.toCharArray(),
                newUserPassword = newPassword.toCharArray(),
                newUserSalt = newSalt
            ).flatMap { updatedAuthenticationPassword ->
                api.resetUserPassword(
                    request = ResetUserPassword(rawPassword = updatedAuthenticationPassword.extract())
                ).map { updatedAuthenticationPassword.digested() }
            }

            f(result)
        }
    }

    fun updateDeviceSecret(
        plaintextDeviceSecret: ByteString,
        password: String,
        f: (Try<DeviceSecret>) -> Unit
    ) {
        coroutineScope.launch {
            val deviceSecretResult =
                bridge.storeDeviceSecret(plaintextDeviceSecret, password.toCharArray())
            latestDeviceSecret.set(deviceSecretResult)
            f(deviceSecretResult)
        }
    }

    fun pushDeviceSecret(
        api: ServerApiEndpointClient,
        password: String,
        remotePassword: String?,
        f: (Try<Unit>) -> Unit
    ) {
        coroutineScope.launch {
            f(
                bridge.pushDeviceSecret(
                    api = api,
                    userPassword = password.toCharArray(),
                    remotePassword = remotePassword?.toCharArray()
                )
            )
        }
    }

    fun pullDeviceSecret(
        api: ServerApiEndpointClient,
        password: String,
        remotePassword: String?,
        f: (Try<Unit>) -> Unit
    ) {
        coroutineScope.launch {
            val deviceSecretResult = bridge.pullDeviceSecret(
                api = api,
                userPassword = password.toCharArray(),
                remotePassword = remotePassword?.toCharArray()
            )
            if (deviceSecretResult.isSuccess) {
                latestDeviceSecret.set(deviceSecretResult)
            }
            f(deviceSecretResult.map { })
        }
    }

    fun reEncryptDeviceSecret(
        currentPassword: String,
        oldPassword: String,
        f: (Try<Unit>) -> Unit
    ) {
        coroutineScope.launch {
            f(
                bridge.reEncryptDeviceSecret(
                    currentUserPassword = currentPassword.toCharArray(),
                    oldUserPassword = oldPassword.toCharArray()
                )
            )
        }
    }

    fun remoteDeviceSecretExists(
        api: ServerApiEndpointClient,
        f: (Try<Boolean>) -> Unit
    ) {
        coroutineScope.launch {
            f(
                api.deviceKeyExists()
            )
        }
    }

    fun setOnCoreTokenUpdatedHandler(
        observer: Any,
        f: (Try<AccessTokenResponse>) -> Unit,
    ): CredentialsProvider {
        onCoreTokenUpdatedHandlers[observer] = f

        return this
    }

    fun setOnApiTokenUpdatedHandler(
        observer: Any,
        f: (Try<AccessTokenResponse>) -> Unit,
    ): CredentialsProvider {
        onApiTokenUpdatedHandlers[observer] = f

        return this
    }

    private fun JwtContext.expiresIn(): Long =
        Duration.between(
            Instant.now(),
            Instant.ofEpochSecond(this.jwtClaims.expirationTime.value)
        ).abs().seconds

    data class Config(
        val coreScope: String,
        val apiScope: String,
        val expirationTolerance: Duration,
    )
}
