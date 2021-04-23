package stasis.client_android.lib.encryption.secrets

import okio.ByteString
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId

data class UserEncryptionSecret(
    val user: UserId,
    val iv: ByteString,
    private val key: ByteString,
    val target: Config
) : Secret() {
    suspend fun encryptDeviceSecret(
        secret: DeviceSecret
    ): ByteString =
        secret.encrypted { Aes.encryption(it, key, iv) }

    suspend fun decryptDeviceSecret(
        device: DeviceId,
        encryptedSecret: ByteString
    ): DeviceSecret =
        DeviceSecret.decrypted(
            user = user,
            device = device,
            encryptedSecret = encryptedSecret,
            decryptionStage = { Aes.decryption(it, key, iv) },
            target = target
        )
}
