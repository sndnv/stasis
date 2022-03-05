package stasis.test.client_android.lib.encryption.stream

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.buffer
import stasis.client_android.lib.encryption.stream.CipherSink.Companion.cipherSink
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class CipherSinkSpec : WordSpec({
    "A CipherSink" should {
        val ivSize = 12 // bytes
        val keySize = 16 // bytes
        val tagSize = 16 // bytes

        val iv = Random.nextBytes(ivSize)
        val key = Random.nextBytes(keySize)

        fun createCipher(mode: Int): Cipher {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(tagSize * 8, iv)

            cipher.init(mode, SecretKeySpec(key, "AES"), spec)

            return cipher
        }

        "encrypting and decrypting data" {
            val plaintextData = Random.nextBytes(size = 20 * 1024).toByteString()

            val actualEncryptedData = Buffer()
            val actualDecryptedData = Buffer()

            actualEncryptedData.cipherSink(
                cipher = createCipher(mode = Cipher.ENCRYPT_MODE)
            ).buffer().use { it.write(plaintextData) }

            actualDecryptedData.cipherSink(
                cipher = createCipher(mode = Cipher.DECRYPT_MODE)
            ).buffer().use { it.write(actualEncryptedData, actualEncryptedData.size) }

            actualDecryptedData.readByteString() shouldBe (plaintextData)
        }
    }
})
