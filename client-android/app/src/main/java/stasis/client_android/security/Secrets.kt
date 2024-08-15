package stasis.client_android.security

import android.content.SharedPreferences
import okio.ByteString
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.encryption.secrets.UserPassword
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Companion.flatten
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository.Companion.getEncryptedDeviceSecret
import stasis.client_android.persistence.config.ConfigRepository.Companion.getSecretsConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.putEncryptedDeviceSecret
import java.util.concurrent.ThreadLocalRandom

object Secrets {
    const val DefaultDeviceSecretSize: Int = 128

    fun localDeviceSecretExists(
        preferences: SharedPreferences,
    ): Boolean = Try { preferences.getEncryptedDeviceSecret() }.isSuccess

    suspend fun createDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
        device: DeviceId,
        preferences: SharedPreferences,
    ): Try<Unit> = Try {
        val secretsConfig = preferences.getSecretsConfig()

        val rawDeviceSecret = generateRawDeviceSecret(secretSize = DefaultDeviceSecretSize)

        val decryptedDeviceSecret = DeviceSecret(
            user = user,
            device = device,
            secret = rawDeviceSecret,
            target = secretsConfig
        )

        val encryptedDeviceSecret = UserPassword(
            user = user,
            salt = userSalt,
            password = userPassword,
            target = secretsConfig
        ).toHashedEncryptionPassword()
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(secret = decryptedDeviceSecret)

        preferences.putEncryptedDeviceSecret(secret = encryptedDeviceSecret)
    }

    suspend fun loadDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
        device: DeviceId,
        preferences: SharedPreferences,
    ): Try<DeviceSecret> =
        Try {
            UserPassword(
                user = user,
                salt = userSalt,
                password = userPassword,
                target = preferences.getSecretsConfig()
            ).toHashedEncryptionPassword()
                .toLocalEncryptionSecret()
                .decryptDeviceSecret(
                    device = device,
                    encryptedSecret = preferences.getEncryptedDeviceSecret()
                )
        }

    suspend fun storeDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
        device: DeviceId,
        secret: ByteString,
        preferences: SharedPreferences,
    ): Try<DeviceSecret> = Try {
        val secretsConfig = preferences.getSecretsConfig()

        val decryptedDeviceSecret = DeviceSecret(
            user = user,
            device = device,
            secret = secret,
            target = secretsConfig
        )

        val encryptedDeviceSecret = UserPassword(
            user = user,
            salt = userSalt,
            password = userPassword,
            target = secretsConfig
        ).toHashedEncryptionPassword()
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(secret = decryptedDeviceSecret)

        preferences.putEncryptedDeviceSecret(secret = encryptedDeviceSecret)

        decryptedDeviceSecret
    }

    suspend fun pushDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
        remotePassword: CharArray?,
        device: DeviceId,
        preferences: SharedPreferences,
        api: ServerApiEndpointClient
    ): Try<Unit> = Try {
        val secretsConfig = preferences.getSecretsConfig()

        val userEncryptionPassword = UserPassword(
            user = user,
            salt = userSalt,
            password = userPassword,
            target = secretsConfig
        ).toHashedEncryptionPassword()

        val decryptedDeviceSecret = userEncryptionPassword
            .toLocalEncryptionSecret()
            .decryptDeviceSecret(
                device = device,
                encryptedSecret = preferences.getEncryptedDeviceSecret()
            )

        val keyStoreEncryptionSecret = when (remotePassword) {
            null -> userEncryptionPassword.toKeyStoreEncryptionSecret()

            else -> UserPassword(
                user = user,
                salt = userSalt,
                password = remotePassword,
                target = secretsConfig
            ).toHashedEncryptionPassword().toKeyStoreEncryptionSecret()
        }

        val encryptedDeviceSecret = keyStoreEncryptionSecret
            .encryptDeviceSecret(secret = decryptedDeviceSecret)

        encryptedDeviceSecret
    }.flatMap { api.pushDeviceKey(it) }

    suspend fun pullDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
        remotePassword: CharArray?,
        device: DeviceId,
        preferences: SharedPreferences,
        api: ServerApiEndpointClient
    ): Try<DeviceSecret> = api.pullDeviceKey().flatMap { encryptedDeviceSecret ->
        val secretsConfig = preferences.getSecretsConfig()

        val userEncryptionPassword = UserPassword(
            user = user,
            salt = userSalt,
            password = userPassword,
            target = secretsConfig
        ).toHashedEncryptionPassword()

        val keyStoreEncryptionSecret = when (remotePassword) {
            null -> userEncryptionPassword.toKeyStoreEncryptionSecret()

            else -> UserPassword(
                user = user,
                salt = userSalt,
                password = remotePassword,
                target = secretsConfig
            ).toHashedEncryptionPassword().toKeyStoreEncryptionSecret()
        }

        val decryptedDeviceSecret = keyStoreEncryptionSecret.decryptDeviceSecret(
            device = device,
            encryptedSecret = encryptedDeviceSecret
        )

        val reEncryptedDeviceSecret = userEncryptionPassword
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(secret = decryptedDeviceSecret)

        preferences.putEncryptedDeviceSecret(secret = reEncryptedDeviceSecret)

        Success(decryptedDeviceSecret)
    }

    suspend fun reEncryptDeviceSecret(
        user: UserId,
        currentUserSalt: String,
        currentUserPassword: CharArray,
        newUserSalt: String,
        newUserPassword: CharArray,
        device: DeviceId,
        preferences: SharedPreferences,
        api: ServerApiEndpointClient?
    ): Try<Unit> = Try {
        val secretsConfig = preferences.getSecretsConfig()

        val currentUserEncryptionPassword = UserPassword(
            user = user,
            salt = currentUserSalt,
            password = currentUserPassword,
            target = secretsConfig
        ).toHashedEncryptionPassword()

        val newUserEncryptionPassword = UserPassword(
            user = user,
            salt = newUserSalt,
            password = newUserPassword,
            target = secretsConfig
        ).toHashedEncryptionPassword()

        val decryptedDeviceSecret = currentUserEncryptionPassword
            .toLocalEncryptionSecret()
            .decryptDeviceSecret(
                device = device,
                encryptedSecret = preferences.getEncryptedDeviceSecret()
            )

        val localEncryptedDeviceSecret = newUserEncryptionPassword
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(decryptedDeviceSecret)

        preferences.putEncryptedDeviceSecret(localEncryptedDeviceSecret)

        when (api) {
            null -> Success(Unit) // do nothing
            else -> {
                api.deviceKeyExists().flatMap { exists ->
                    if (exists) {
                        val keyStoreEncryptedDeviceSecret = newUserEncryptionPassword
                            .toKeyStoreEncryptionSecret()
                            .encryptDeviceSecret(decryptedDeviceSecret)

                        api.pushDeviceKey(keyStoreEncryptedDeviceSecret)
                    } else {
                        Success(Unit) // do nothing
                    }
                }
            }
        }
    }.flatten()

    fun loadUserAuthenticationPassword(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
        preferences: SharedPreferences,
    ): UserAuthenticationPassword = UserPassword(
        user = user,
        salt = userSalt,
        password = userPassword,
        target = preferences.getSecretsConfig()
    ).toAuthenticationPassword()

    fun initDeviceSecret(
        user: UserId,
        device: DeviceId,
        secret: ByteString,
        preferences: SharedPreferences,
    ): DeviceSecret = DeviceSecret(
        user = user,
        device = device,
        secret = secret,
        target = preferences.getSecretsConfig()
    )

    fun generateRawDeviceSecret(secretSize: Int): ByteString {
        val rnd = ThreadLocalRandom.current()
        val bytes = ByteArray(size = secretSize)
        rnd.nextBytes(bytes)

        return bytes.toByteString()
    }
}
