package stasis.client_android.mocks

import okio.ByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.security.CredentialsManagementBridge
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success

open class MockCredentialsManagementBridge(
    val deviceSecret: DeviceSecret,
    val authenticationPassword: UserAuthenticationPassword
) : CredentialsManagementBridge {
    override fun initDeviceSecret(secret: ByteString): DeviceSecret =
        deviceSecret

    override suspend fun loadDeviceSecret(userPassword: CharArray): Try<DeviceSecret> =
        Success(deviceSecret)

    override suspend fun storeDeviceSecret(secret: ByteString, userPassword: CharArray): Try<DeviceSecret> =
        Success(deviceSecret)

    override suspend fun pushDeviceSecret(api: ServerApiEndpointClient, userPassword: CharArray): Try<Unit> =
        Success(Unit)

    override suspend fun pullDeviceSecret(api: ServerApiEndpointClient, userPassword: CharArray): Try<DeviceSecret> =
        Success(deviceSecret)

    override fun initDigestedUserPassword(digestedUserPassword: String) =
        Unit

    override fun verifyUserPassword(userPassword: CharArray): Boolean =
        false

    override suspend fun updateUserCredentials(
        currentUserPassword: CharArray,
        newUserPassword: CharArray,
        newUserSalt: String?
    ): Try<UserAuthenticationPassword> =
        when (authenticationPassword) {
            is UserAuthenticationPassword.Hashed -> Success(authenticationPassword.copy())
            is UserAuthenticationPassword.Unhashed -> Success(authenticationPassword.copy())
        }

    override fun getAuthenticationPassword(userPassword: CharArray): UserAuthenticationPassword =
        when (authenticationPassword) {
            is UserAuthenticationPassword.Hashed -> authenticationPassword.copy()
            is UserAuthenticationPassword.Unhashed -> authenticationPassword.copy()
        }
}
