package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.Sink
import okio.Source
import okio.buffer
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import java.nio.file.Paths

class DeviceFileSecretSpec : WordSpec({
    "A DeviceFileSecret" should {
        val encryptionIv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
        val encryptionKey = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

        val plaintextData = "some-plaintext-data"
        val encryptedData = "coKEpIHZVHZtPcYuojvMPcOD8S6a2sH4Xg0gPX8UTKz75Ts="

        val fileSecret = DeviceFileSecret(
            file = Paths.get("/tmp/some/file"),
            iv = encryptionIv.decodeBase64()!!,
            key = encryptionKey.decodeBase64()!!
        )

        "support encryption (source)" {
            val actualEncryptedData = fileSecret
                .encryption(Buffer().writeUtf8(plaintextData) as Source)
                .buffer()
                .readByteString()

            actualEncryptedData shouldBe (encryptedData.decodeBase64()!!)
        }

        "support encryption (sink)" {
            val actualEncryptedData = Buffer()

            fileSecret
                .encryption(actualEncryptedData as Sink)
                .buffer()
                .use {
                    it.writeAll(Buffer().writeUtf8(plaintextData))
                }

            actualEncryptedData.readByteString() shouldBe (encryptedData.decodeBase64()!!)
        }

        "support decryption" {
            val actualPlaintextData = fileSecret
                .decryption(Buffer().write(encryptedData.decodeBase64()!!))
                .buffer()
                .readUtf8()

            actualPlaintextData shouldBe (plaintextData)
        }

        "not render its content via toString" {
            fileSecret.toString() shouldBe ("Secret(${fileSecret.javaClass.name})")
        }
    }
})
