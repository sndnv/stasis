package stasis.test.client_android.lib.encryption.stream

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.stream.CipherTransformation
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CipherTransformationSpec : WordSpec({
    "A CipherTransformation" should {
        val aesEncryptionKey = "PeKeM+i2PY1zTYrj52HnPg=="
        val aesEncryptionIv = "i+/GSKy4En8O5nj9U4z7tA=="

        val plaintextData = "some-plaintext-data"

        val aesEncryptedData = "DW/YzX/aXwio9P25x8XgNKHaDgKcEHyjBgIkVXPY2Acxt/s="

        "encrypt data (AES via source)" {
            val source = Buffer().write(plaintextData.toByteArray())

            val transformation = CipherTransformation.process(
                source = source,
                algorithm = "AES",
                cipherMode = "GCM",
                padding = "NoPadding",
                operationMode = Cipher.ENCRYPT_MODE,
                key = SecretKeySpec(aesEncryptionKey.decodeBase64()?.toByteArray(), "AES"),
                spec = GCMParameterSpec(Aes.TagSize, aesEncryptionIv.decodeBase64()?.toByteArray())
            )

            val actualEncryptedData = transformation.buffer().readUtf8()
            actualEncryptedData shouldBe (aesEncryptedData.decodeBase64()?.utf8())
        }

        "decrypt data (AES via source)" {
            val source = Buffer().write(aesEncryptedData.decodeBase64()!!.toByteArray())

            val transformation = CipherTransformation.process(
                source = source,
                algorithm = "AES",
                cipherMode = "GCM",
                padding = "NoPadding",
                operationMode = Cipher.DECRYPT_MODE,
                key = SecretKeySpec(aesEncryptionKey.decodeBase64()?.toByteArray(), "AES"),
                spec = GCMParameterSpec(Aes.TagSize, aesEncryptionIv.decodeBase64()?.toByteArray())
            )

            val actualPlaintextData = transformation.buffer().readUtf8()
            actualPlaintextData shouldBe (plaintextData)
        }

        "encrypt data (AES via sink)" {
            val actualEncryptedData = Buffer()

            val transformation = CipherTransformation.process(
                sink = actualEncryptedData,
                algorithm = "AES",
                cipherMode = "GCM",
                padding = "NoPadding",
                operationMode = Cipher.ENCRYPT_MODE,
                key = SecretKeySpec(aesEncryptionKey.decodeBase64()?.toByteArray(), "AES"),
                spec = GCMParameterSpec(Aes.TagSize, aesEncryptionIv.decodeBase64()?.toByteArray())
            )

            transformation.buffer().use { it.write(plaintextData.toByteArray()) }
            actualEncryptedData.readUtf8() shouldBe (aesEncryptedData.decodeBase64()?.utf8())
        }

        "decrypt data (AES via sink)" {
            val actualPlaintextData = Buffer()

            val transformation = CipherTransformation.process(
                sink = actualPlaintextData,
                algorithm = "AES",
                cipherMode = "GCM",
                padding = "NoPadding",
                operationMode = Cipher.DECRYPT_MODE,
                key = SecretKeySpec(aesEncryptionKey.decodeBase64()?.toByteArray(), "AES"),
                spec = GCMParameterSpec(Aes.TagSize, aesEncryptionIv.decodeBase64()?.toByteArray())
            )

            transformation.buffer().use { it.write(aesEncryptedData.decodeBase64()!!) }
            actualPlaintextData.readUtf8() shouldBe (plaintextData)
        }

        "encrypt data (AES / ECB)" {
            // warning: for testing purposes only; do not use AES in ECB mode!

            val source =
                Buffer().write("1234567890ABCDEF".toByteArray()) // 16 bytes in size, to avoid using padding

            val transformation = CipherTransformation.process(
                source = source,
                algorithm = "AES",
                cipherMode = "ECB",
                padding = "NoPadding",
                operationMode = Cipher.ENCRYPT_MODE,
                key = SecretKeySpec(aesEncryptionKey.decodeBase64()?.toByteArray(), "AES"),
                spec = null
            )

            val actualEncryptedData = transformation.buffer().readUtf8()
            actualEncryptedData shouldBe ("5BgTzTE1hYHU95bcG7udNA==".decodeBase64()?.utf8())
        }

        "decrypt data (AES / ECB)" {
            // warning: for testing purposes only; do not use AES in ECB mode!

            val source = Buffer().write("5BgTzTE1hYHU95bcG7udNA==".decodeBase64()!!.toByteArray())

            val transformation = CipherTransformation.process(
                source = source,
                algorithm = "AES",
                cipherMode = "ECB",
                padding = "NoPadding",
                operationMode = Cipher.DECRYPT_MODE,
                key = SecretKeySpec(aesEncryptionKey.decodeBase64()?.toByteArray(), "AES"),
                spec = null
            )

            val actualPlaintextData = transformation.buffer().readUtf8()
            actualPlaintextData shouldBe ("1234567890ABCDEF")
        }
    }
})
