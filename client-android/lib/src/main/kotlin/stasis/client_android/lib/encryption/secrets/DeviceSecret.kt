package stasis.client_android.lib.encryption.secrets

import at.favre.lib.crypto.HKDF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Source
import okio.buffer
import stasis.client_android.lib.model.core.CrateId
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId
import java.nio.file.Path

data class DeviceSecret(
    val user: UserId,
    val device: DeviceId,
    private val secret: ByteString,
    val target: Config
) : Secret() {
    suspend fun encrypted(encryptionStage: (Source) -> Source): ByteString =
        withContext(Dispatchers.IO) {
            encryptionStage(
                Buffer().write(secret)
            ).buffer().use { it.readByteString() }
        }

    fun toFileSecret(forFile: Path): DeviceFileSecret {
        val filePath = forFile.toAbsolutePath().toString()

        val salt = user.toBytes() + device.toBytes() + filePath.encodeToByteArray()

        val keyInfo = "$user-$device-$filePath-key".encodeUtf8()
        val ivInfo = "$user-$device-$filePath-iv".encodeUtf8()

        val hkdf = HKDF.fromHmacSha512()

        val prk = hkdf.extract(salt, secret.toByteArray())

        val key = hkdf.expand(prk, keyInfo.toByteArray(), target.encryption.file.keySize)
        val iv = hkdf.expand(prk, ivInfo.toByteArray(), target.encryption.file.ivSize)

        return DeviceFileSecret(
            file = forFile,
            key = key.toByteString(),
            iv = iv.toByteString()
        )
    }

    fun toMetadataSecret(metadataCrate: CrateId): DeviceMetadataSecret {
        val salt = user.toBytes() + device.toBytes() + metadataCrate.toBytes()

        val keyInfo = "$user-$device-$metadataCrate-key".encodeUtf8()
        val ivInfo = "$user-$device-$metadataCrate-iv".encodeUtf8()

        val hkdf = HKDF.fromHmacSha512()

        val prk = hkdf.extract(salt, secret.toByteArray())

        val key = hkdf.expand(prk, keyInfo.toByteArray(), target.encryption.metadata.keySize)
        val iv = hkdf.expand(prk, ivInfo.toByteArray(), target.encryption.metadata.ivSize)

        return DeviceMetadataSecret(
            key = key.toByteString(),
            iv = iv.toByteString()
        )
    }

    companion object {
        suspend fun decrypted(
            user: UserId,
            device: DeviceId,
            encryptedSecret: ByteString,
            decryptionStage: (Source) -> Source,
            target: Config
        ): DeviceSecret {
            val secret = withContext(Dispatchers.IO) {
                decryptionStage(
                    Buffer().write(encryptedSecret)
                ).buffer().use { it.readByteString() }
            }

            return DeviceSecret(
                user = user,
                device = device,
                secret = secret,
                target = target
            )
        }
    }
}
