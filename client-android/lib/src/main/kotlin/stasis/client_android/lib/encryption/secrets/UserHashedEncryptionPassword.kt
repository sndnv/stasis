package stasis.client_android.lib.encryption.secrets

import at.favre.lib.hkdf.HKDF
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.model.server.users.UserId

data class UserHashedEncryptionPassword(
    val user: UserId,
    private val hashedPassword: ByteString,
    val target: Config
) : Secret() {
    fun toLocalEncryptionSecret(): UserLocalEncryptionSecret {
        val salt = user.toBytes()
        val keyInfo = "${user}-encryption-key".encodeUtf8()
        val ivInfo = "${user}-encryption-iv".encodeUtf8()

        val hkdf = HKDF.fromHmacSha512()

        val prk = hkdf.extract(salt, hashedPassword.toByteArray())

        val key = hkdf.expand(prk, keyInfo.toByteArray(), target.encryption.deviceSecret.keySize)
        val iv = hkdf.expand(prk, ivInfo.toByteArray(), target.encryption.deviceSecret.ivSize)

        return UserLocalEncryptionSecret(
            user = user,
            key = key.toByteString(),
            iv = iv.toByteString(),
            target = target
        )
    }

    fun toKeyStoreEncryptionSecret(): UserKeyStoreEncryptionSecret {
        val salt = user.toBytes()
        val keyInfo = "${user}-key-store-encryption-key".encodeUtf8()
        val ivInfo = "${user}-key-store-encryption-iv".encodeUtf8()

        val hkdf = HKDF.fromHmacSha512()

        val prk = hkdf.extract(salt, hashedPassword.toByteArray())

        val key = hkdf.expand(prk, keyInfo.toByteArray(), target.encryption.deviceSecret.keySize)
        val iv = hkdf.expand(prk, ivInfo.toByteArray(), target.encryption.deviceSecret.ivSize)

        return UserKeyStoreEncryptionSecret(
            user = user,
            key = key.toByteString(),
            iv = iv.toByteString(),
            target = target
        )
    }
}
