package stasis.client_android.security

import android.content.SharedPreferences
import okio.ByteString
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.api.clients.ServerApiEndpointClient
import stasis.client_android.lib.api.clients.exceptions.ResourceMissingFailure
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.encryption.secrets.UserPassword
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId
import stasis.client_android.lib.utils.Try
import stasis.client_android.lib.utils.Try.Companion.flatMap
import stasis.client_android.lib.utils.Try.Failure
import stasis.client_android.lib.utils.Try.Success
import stasis.client_android.persistence.config.ConfigRepository.Companion.getEncryptedDeviceSecret
import stasis.client_android.persistence.config.ConfigRepository.Companion.getSecretsConfig
import stasis.client_android.persistence.config.ConfigRepository.Companion.putEncryptedDeviceSecret
import java.util.concurrent.ThreadLocalRandom

object Secrets {
    const val DefaultDeviceSecretSize: Int = 128

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

        val encryptedDeviceSecret = userEncryptionPassword
            .toKeyStoreEncryptionSecret()
            .encryptDeviceSecret(secret = decryptedDeviceSecret)

        encryptedDeviceSecret
    }.flatMap { api.pushDeviceKey(it) }

    suspend fun pullDeviceSecret(
        user: UserId,
        userSalt: String,
        userPassword: CharArray,
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

        val decryptedDeviceSecret = userEncryptionPassword
            .toKeyStoreEncryptionSecret()
            .decryptDeviceSecret(
                device = device,
                encryptedSecret = encryptedDeviceSecret
            )

        val reEncryptedDeviceSecret = userEncryptionPassword
            .toLocalEncryptionSecret()
            .encryptDeviceSecret(secret = decryptedDeviceSecret)

        preferences.putEncryptedDeviceSecret(secret = reEncryptedDeviceSecret)

        Success(decryptedDeviceSecret)
    }

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
