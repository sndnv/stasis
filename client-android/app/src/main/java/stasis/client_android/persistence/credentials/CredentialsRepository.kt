package stasis.client_android.persistence.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import okio.ByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.security.AccessTokenResponse
import stasis.client_android.lib.security.CredentialsProvider
import stasis.client_android.lib.utils.Reference
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.foreach
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.Converters.Companion.toAccessTokenResponse
import stasis.client_android.persistence.Converters.Companion.toJson
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import stasis.client_android.serialization.ByteStrings.decodeFromBase64
import stasis.client_android.serialization.ByteStrings.encodeAsBase64

class CredentialsRepository(
    private val configPreferences: SharedPreferences,
    private val credentialsPreferences: SharedPreferences,
    private val contextFactory: ProviderContext.Factory,
) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val contextRef: Reference<ProviderContext> =
        contextFactory.getOrCreate(configPreferences)

    private val providerRef: Reference<CredentialsProvider> =
        contextRef.map { providerContext ->
            providerContext.credentials
                .setOnCoreTokenUpdatedHandler(this) { updateToken(it, Keys.DeviceToken) }
                .setOnApiTokenUpdatedHandler(this) { updateToken(it, Keys.UserToken) }
        }

    private val userData: MutableLiveData<AccessTokenResponse?> =
        MutableLiveData(credentialsPreferences.getUserAccessToken())

    private val deviceData: MutableLiveData<AccessTokenResponse?> =
        MutableLiveData(credentialsPreferences.getDeviceAccessToken())

    val user: LiveData<Try<AccessTokenResponse.Claims>> = userData.map { it.claims() }
    val device: LiveData<Try<AccessTokenResponse.Claims>> = deviceData.map { it.claims() }

    val available: Boolean
        get() = credentialsPreferences.getTokens()?.let { (userToken, deviceToken) ->
            userToken.hasNotExpired && deviceToken.hasNotExpired
        } ?: false

    init {
        credentialsPreferences.registerOnSharedPreferenceChangeListener(this)

        credentialsPreferences.getTokens()?.let { (userToken, deviceToken) ->
            credentialsPreferences.getPlaintextDeviceSecret()?.let { plaintextDeviceSecret ->
                credentialsPreferences.getDigestedUserPassword()?.let { digestedUserPassword ->
                    withOAuthContext { provider ->
                        provider.init(
                            coreToken = deviceToken,
                            apiToken = userToken,
                            plaintextDeviceSecret = plaintextDeviceSecret,
                            digestedUserPassword = digestedUserPassword
                        )
                    }
                }
            }
        }
    }

    fun login(username: String, password: String, f: (Try<Unit>) -> Unit) {
        when (
            withOAuthContext { provider ->
                provider.login(username, password) { result ->
                    result.foreach {
                        credentialsPreferences.putPlaintextDeviceSecret(it.first.secret)
                        credentialsPreferences.putDigestedUserPassword(it.second)
                    }
                    f(result.map {})
                }
            }
        ) {
            null -> f(Failure(RuntimeException("Client not configured")))
            else -> Unit // do nothing
        }
    }

    fun logout(f: () -> Unit) {
        when (
            withOAuthContext { provider ->
                provider.logout()

                credentialsPreferences
                    .edit(commit = true) {
                        remove(Keys.UserToken)
                            .remove(Keys.DeviceToken)
                            .remove(Keys.PlaintextDeviceSecret)
                            .remove(Keys.DigestedUserPassword)
                    }

                f()
            }
        ) {
            null -> f()
            else -> Unit // do nothing
        }
    }

    fun verifyUserPassword(password: String, f: (Boolean) -> Unit) {
        when (
            withOAuthContext { provider ->
                provider.verifyUserPassword(password = password, f = f)
            }
        ) {
            null -> f(false)
            else -> Unit // do nothing
        }
    }

    fun updateUserCredentials(
        api: ServerApiEndpointClient,
        currentPassword: String,
        newPassword: String,
        newSalt: String?,
        f: (Try<Unit>) -> Unit
    ) = when (
        withOAuthContext { provider ->
            provider.updateUserCredentials(
                api = api,
                currentPassword = currentPassword,
                newPassword = newPassword,
                newSalt = newSalt
            ) { result ->
                result.foreach {
                    credentialsPreferences.putDigestedUserPassword(it)
                }
                f(result.map {})
            }
        }
    ) {
        null -> f(Failure(RuntimeException("Client not configured")))
        else -> Unit // do nothing
    }

    fun updateDeviceSecret(password: String, secret: ByteString, f: (Try<Unit>) -> Unit) {
        when (
            withOAuthContext { provider ->
                provider.updateDeviceSecret(
                    plaintextDeviceSecret = secret,
                    password = password
                ) { result ->
                    result.foreach { credentialsPreferences.putPlaintextDeviceSecret(it.secret) }
                    f(result.map {})
                }
            }
        ) {
            null -> f(Failure(RuntimeException("Client not configured")))
            else -> Unit // do nothing
        }
    }

    fun pushDeviceSecret(
        api: ServerApiEndpointClient,
        password: String,
        remotePassword: String?,
        f: (Try<Unit>) -> Unit
    ) {
        when (
            withOAuthContext { provider ->
                provider.pushDeviceSecret(
                    api = api,
                    password = password,
                    remotePassword = remotePassword,
                    f = f
                )
            }
        ) {
            null -> f(Failure(RuntimeException("Client not configured")))
            else -> Unit // do nothing
        }
    }

    fun pullDeviceSecret(
        api: ServerApiEndpointClient,
        password: String,
        remotePassword: String?,
        f: (Try<Unit>) -> Unit
    ) {
        when (
            withOAuthContext { provider ->
                provider.pullDeviceSecret(
                    api = api,
                    password = password,
                    remotePassword = remotePassword,
                    f = f
                )
            }
        ) {
            null -> f(Failure(RuntimeException("Client not configured")))
            else -> Unit // do nothing
        }
    }

    fun reEncryptDeviceSecret(
        currentPassword: String,
        oldPassword: String,
        f: (Try<Unit>) -> Unit
    ) {
        when (
            withOAuthContext { provider ->
                provider.reEncryptDeviceSecret(
                    currentPassword = currentPassword,
                    oldPassword = oldPassword,
                    f = f
                )
            }
        ) {
            null -> f(Failure(RuntimeException("Client not configured")))
            else -> Unit // do nothing
        }
    }

    fun remoteDeviceSecretExists(
        api: ServerApiEndpointClient,
        f: (Try<Boolean>) -> Unit
    ) {
        when (
            withOAuthContext { provider ->
                provider.remoteDeviceSecretExists(api = api, f = f)
            }
        ) {
            null -> f(Failure(RuntimeException("Client not configured")))
            else -> Unit // do nothing
        }
    }

    override fun onSharedPreferenceChanged(updated: SharedPreferences?, key: String?) {
        when (key) {
            Keys.UserToken -> userData.postValue(updated?.getUserAccessToken())
            Keys.DeviceToken -> deviceData.postValue(updated?.getDeviceAccessToken())
            else -> Unit // do nothing
        }
    }

    private inline fun <reified T> withOAuthContext(crossinline f: (CredentialsProvider) -> T): T? =
        providerRef.provided { f(it) }

    private fun updateToken(
        tokenResponse: Try<AccessTokenResponse>,
        tokenKey: String,
    ) {
        when (tokenResponse) {
            is Success -> credentialsPreferences.putToken(tokenKey, tokenResponse.value)
            is Failure -> credentialsPreferences.remove(tokenKey)
        }
    }

    private fun SharedPreferences.putToken(key: String, token: AccessTokenResponse) =
        this.edit { putString(key, token.toJson()) }


    private fun SharedPreferences.getTokens(): Pair<AccessTokenResponse, AccessTokenResponse>? =
        this.getUserAccessToken()?.let { userToken ->
            this.getDeviceAccessToken()?.let { deviceToken ->
                userToken to deviceToken
            }
        }

    private fun SharedPreferences.getUserAccessToken(): AccessTokenResponse? =
        this.getString(Keys.UserToken, null)?.toAccessTokenResponse()

    private fun SharedPreferences.getDeviceAccessToken(): AccessTokenResponse? =
        this.getString(Keys.DeviceToken, null)?.toAccessTokenResponse()

    private fun SharedPreferences.remove(key: String) =
        this.edit { putString(key, null) }

    private fun AccessTokenResponse?.claims(): Try<AccessTokenResponse.Claims> =
        this?.claims ?: Failure(RuntimeException("No access token found"))

    companion object {
        object Keys {
            const val UserToken: String = "user_token"
            const val DeviceToken: String = "device_token"
            const val PlaintextDeviceSecret: String = "device_secret"
            const val DigestedUserPassword: String = "digested_user_password"
        }

        const val EncryptedPreferencesFileName: String =
            "stasis.client_android.persistence.credentials"

        @Volatile
        private var INSTANCE: CredentialsRepository? = null

        operator fun invoke(
            contextFactory: ProviderContext.Factory,
            context: Context,
        ): CredentialsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: createInstance(contextFactory, context).also { INSTANCE = it }
            }

        fun createInstance(
            contextFactory: ProviderContext.Factory,
            context: Context,
        ): CredentialsRepository =
            CredentialsRepository(
                configPreferences = ConfigRepository.getPreferences(context),
                credentialsPreferences = getEncryptedPreferences(context),
                contextFactory = contextFactory
            )

        fun getEncryptedPreferences(context: Context): SharedPreferences {
            return androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                EncryptedPreferencesFileName,
                androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        fun SharedPreferences.putPlaintextDeviceSecret(secret: ByteString) {
            this
                .edit(commit = true) {
                    putString(Keys.PlaintextDeviceSecret, secret.encodeAsBase64())
                }
        }

        fun SharedPreferences.getPlaintextDeviceSecret(): ByteString? =
            this.getString(Keys.PlaintextDeviceSecret, null)?.decodeFromBase64()

        fun SharedPreferences.putDigestedUserPassword(password: String) {
            this
                .edit(commit = true) {
                    putString(Keys.DigestedUserPassword, password)
                }
        }

        fun SharedPreferences.getDigestedUserPassword(): String? =
            this.getString(Keys.DigestedUserPassword, null)
    }
}
