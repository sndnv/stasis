package stasis.client_android.security

import android.content.SharedPreferences
import okio.ByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId
import stasis.client_android.lib.security.CredentialsManagementBridge
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.map
import stasis.client_android.persistence.config.Config
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class DefaultCredentialsManagementBridge(
    apiConfig: Config.ServerApi,
    private val preferences: SharedPreferences,
) : CredentialsManagementBridge {
    val user: UserId = UUID.fromString(apiConfig.user)
    val device: DeviceId = UUID.fromString(apiConfig.device)

    private val userSaltRef: AtomicReference<String> = AtomicReference(apiConfig.userSalt)
    private val digestedUserPasswordRef: AtomicReference<String?> = AtomicReference(null)

    override fun initDeviceSecret(
        secret: ByteString
    ): DeviceSecret = Secrets.initDeviceSecret(
        user = user, device = device, secret = secret, preferences = preferences
    )

    override suspend fun loadDeviceSecret(
        userPassword: CharArray
    ): Try<DeviceSecret> = Secrets.loadDeviceSecret(
        user = user,
        userSalt = userSaltRef.get(),
        userPassword = userPassword,
        device = device,
        preferences = preferences
    )

    override suspend fun storeDeviceSecret(
        secret: ByteString, userPassword: CharArray
    ): Try<DeviceSecret> = Secrets.storeDeviceSecret(
        user = user,
        userSalt = userSaltRef.get(),
        userPassword = userPassword,
        device = device,
        secret = secret,
        preferences = preferences
    )

    override suspend fun pushDeviceSecret(
        api: ServerApiEndpointClient,
        userPassword: CharArray,
        remotePassword: CharArray?
    ): Try<Unit> = Secrets.pushDeviceSecret(
        user = user,
        userSalt = userSaltRef.get(),
        userPassword = userPassword,
        remotePassword = remotePassword,
        device = device,
        preferences = preferences,
        api = api
    )

    override suspend fun pullDeviceSecret(
        api: ServerApiEndpointClient,
        userPassword: CharArray,
        remotePassword: CharArray?
    ): Try<DeviceSecret> = Secrets.pullDeviceSecret(
        user = user,
        userSalt = userSaltRef.get(),
        userPassword = userPassword,
        remotePassword = remotePassword,
        device = device,
        preferences = preferences,
        api = api
    )

    override fun initDigestedUserPassword(
        digestedUserPassword: String
    ) = digestedUserPasswordRef.set(digestedUserPassword)

    override fun verifyUserPassword(
        userPassword: CharArray
    ): Boolean {
        val existing = digestedUserPasswordRef.get()

        val provided = Secrets.loadUserAuthenticationPassword(
            user = user, userSalt = userSaltRef.get(), userPassword = userPassword, preferences = preferences
        ).digested()

        return existing == provided
    }

    override suspend fun updateUserCredentials(
        api: ServerApiEndpointClient,
        currentUserPassword: CharArray,
        newUserPassword: CharArray,
        newUserSalt: String?
    ): Try<UserAuthenticationPassword> {
        val actualUserSalt = newUserSalt ?: userSaltRef.get()

        return Secrets.reEncryptDeviceSecret(
            user = user,
            currentUserSalt = userSaltRef.get(),
            currentUserPassword = currentUserPassword,
            newUserSalt = actualUserSalt,
            newUserPassword = newUserPassword,
            device = device,
            preferences = preferences,
            api = api
        ).map {
            val newUserAuthenticationPassword = Secrets.loadUserAuthenticationPassword(
                user = user,
                userSalt = actualUserSalt,
                userPassword = newUserPassword,
                preferences = preferences
            )

            userSaltRef.set(actualUserSalt)
            digestedUserPasswordRef.set(newUserAuthenticationPassword.digested())

            newUserAuthenticationPassword
        }
    }

    override suspend fun reEncryptDeviceSecret(
        currentUserPassword: CharArray,
        oldUserPassword: CharArray
    ): Try<Unit> {
        val actualUserSalt = userSaltRef.get()

        return Secrets.reEncryptDeviceSecret(
            user = user,
            currentUserSalt = actualUserSalt,
            currentUserPassword = oldUserPassword,
            newUserSalt = actualUserSalt,
            newUserPassword = currentUserPassword,
            device = device,
            preferences = preferences,
            api = null
        )
    }


    override fun getAuthenticationPassword(
        userPassword: CharArray
    ): UserAuthenticationPassword = Secrets.loadUserAuthenticationPassword(
        user = user, userSalt = userSaltRef.get(), userPassword = userPassword, preferences = preferences
    )
}