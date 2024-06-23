package stasis.client_android.lib.security

import okio.ByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.utils.Try

interface CredentialsManagementBridge {
    fun initDeviceSecret(secret: ByteString): DeviceSecret
    suspend fun loadDeviceSecret(userPassword: CharArray): Try<DeviceSecret>
    suspend fun storeDeviceSecret(secret: ByteString, userPassword: CharArray): Try<DeviceSecret>
    suspend fun pushDeviceSecret(api: ServerApiEndpointClient, userPassword: CharArray): Try<Unit>
    suspend fun pullDeviceSecret(api: ServerApiEndpointClient, userPassword: CharArray): Try<DeviceSecret>
    fun initDigestedUserPassword(digestedUserPassword: String)
    fun verifyUserPassword(userPassword: CharArray): Boolean
    fun getAuthenticationPassword(userPassword: CharArray): UserAuthenticationPassword

    suspend fun updateUserCredentials(
        currentUserPassword: CharArray,
        newUserPassword: CharArray,
        newUserSalt: String?
    ): Try<UserAuthenticationPassword>
}
