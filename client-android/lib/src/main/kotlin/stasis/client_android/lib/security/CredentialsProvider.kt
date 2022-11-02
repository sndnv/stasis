package stasis.client_android.lib.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.ByteString
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.JwtConsumer
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.jwt.consumer.JwtContext
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.security.exceptions.MissingDeviceSecret
import stasis.client_android.lib.security.exceptions.TokenExpired
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class CredentialsProvider(
    private val config: Config,
    private val oAuthClient: OAuthClient,
    private val initDeviceSecret: (ByteString) -> DeviceSecret,
    private val loadDeviceSecret: suspend (CharArray) -> Try<DeviceSecret>,
    private val storeDeviceSecret: suspend (ByteString, CharArray) -> Try<DeviceSecret>,
    private val getAuthenticationPassword: (CharArray) -> UserAuthenticationPassword,
    private val coroutineScope: CoroutineScope,
) {
    private val onCoreTokenUpdatedHandlers: ConcurrentHashMap<Any, (Try<AccessTokenResponse>) -> Unit> =
        ConcurrentHashMap()
    private val onApiTokenUpdatedHandlers: ConcurrentHashMap<Any, (Try<AccessTokenResponse>) -> Unit> =
        ConcurrentHashMap()

    private val latestCoreToken: AtomicReference<Try<AccessTokenResponse>> =
        AtomicReference(Failure(RuntimeException("No core access token found")))

    private val latestApiToken: AtomicReference<Try<AccessTokenResponse>> =
        AtomicReference(Failure(RuntimeException("No API access token found")))

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
            latestCoreToken.set(response)
            onCoreTokenUpdatedHandlers.values.forEach { handler -> handler(response) }
        },
        expirationTolerance = config.expirationTolerance,
        coroutineScope = coroutineScope
    )

    private val apiTokenManager: OAuthTokenManager = OAuthTokenManager(
        oAuthClient = oAuthClient,
        onTokenUpdated = { response ->
            latestApiToken.set(response)
            onApiTokenUpdatedHandlers.values.forEach { handler -> handler(response) }
        },
        expirationTolerance = config.expirationTolerance,
        coroutineScope = coroutineScope
    )

    val core: Try<AccessTokenResponse>
        get() = latestCoreToken.get()


    val api: Try<AccessTokenResponse>
        get() = latestApiToken.get()

    val deviceSecret: Try<DeviceSecret>
        get() = latestDeviceSecret.get()

    fun init(
        coreToken: AccessTokenResponse,
        apiToken: AccessTokenResponse,
        plaintextDeviceSecret: ByteString,
    ) {
        try {
            val coreExpiresIn = extractor.process(coreToken.access_token).expiresIn()
            val apiExpiresIn = extractor.process(apiToken.access_token).expiresIn()

            coreTokenManager.scheduleWithClientCredentials(Success(coreToken.copy(expires_in = coreExpiresIn)))
            apiTokenManager.scheduleWithRefreshToken(Success(apiToken.copy(expires_in = apiExpiresIn)))

            latestDeviceSecret.set(Success(initDeviceSecret(plaintextDeviceSecret)))
        } catch (_: InvalidJwtException) {
            when (val apiRefreshToken = apiToken.refresh_token) {
                null -> {
                    coreTokenManager.scheduleWithClientCredentials(Failure(TokenExpired()))
                    apiTokenManager.scheduleWithRefreshToken(Failure(TokenExpired()))
                    latestDeviceSecret.set(Failure(MissingDeviceSecret()))
                }

                else -> init(
                    apiRefreshToken = apiRefreshToken,
                    plaintextDeviceSecret = plaintextDeviceSecret
                )
            }
        }
    }

    fun init(
        apiRefreshToken: String,
        plaintextDeviceSecret: ByteString
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
            latestDeviceSecret.set(Success(initDeviceSecret(plaintextDeviceSecret)))
        }
    }

    fun login(username: String, password: String, f: (Try<DeviceSecret>) -> Unit) {
        coroutineScope.launch {
            when (val deviceSecretResult = loadDeviceSecret(password.toCharArray())) {
                is Success -> {
                    val authenticationPassword = getAuthenticationPassword(password.toCharArray())

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

                    coreTokenManager.scheduleWithClientCredentials(coreTokenResponse)
                    apiTokenManager.scheduleWithRefreshToken(apiTokenResponse)
                    latestDeviceSecret.set(deviceSecretResult)

                    f(coreTokenResponse.flatMap { apiTokenResponse }.flatMap { deviceSecretResult })
                }

                is Failure -> {
                    latestDeviceSecret.set(deviceSecretResult)
                    f(deviceSecretResult)
                }
            }
        }
    }

    fun logout() {
        coreTokenManager.reset()
        apiTokenManager.reset()
        latestDeviceSecret.set(Failure(MissingDeviceSecret()))
    }

    fun updateDeviceSecret(
        plaintextDeviceSecret: ByteString,
        password: String,
        f: (Try<DeviceSecret>) -> Unit
    ) {
        coroutineScope.launch {
            val deviceSecretResult = storeDeviceSecret(plaintextDeviceSecret, password.toCharArray())
            latestDeviceSecret.set(deviceSecretResult)
            f(deviceSecretResult)
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
