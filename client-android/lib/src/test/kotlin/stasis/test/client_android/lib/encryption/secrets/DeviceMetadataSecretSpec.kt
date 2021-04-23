package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.Source
import okio.buffer
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret

class DeviceMetadataSecretSpec : WordSpec({
    "A DeviceMetadataSecret" should {
        val encryptionIv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
        val encryptionKey = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

        val plaintextData = "some-plaintext-data"
        val encryptedData = "coKEpIHZVHZtPcYuojvMPcOD8S6a2sH4Xg0gPX8UTKz75Ts="

        val metadataSecret = DeviceMetadataSecret(
            iv = encryptionIv.decodeBase64()!!,
            key = encryptionKey.decodeBase64()!!
        )

        "support encryption" {
            val actualEncryptedData = metadataSecret
                .encryption(Buffer().writeUtf8(plaintextData) as Source)
                .buffer()
                .readByteString()

            actualEncryptedData shouldBe (encryptedData.decodeBase64()!!)
        }

        "support decryption" {
            val actualPlaintextData = metadataSecret
                .decryption(Buffer().write(encryptedData.decodeBase64()!!))
                .buffer()
                .readUtf8()

            actualPlaintextData shouldBe (plaintextData)
        }

        "not render its content via toString" {
            metadataSecret.toString() shouldBe ("Secret(${metadataSecret.javaClass.name})")
        }
    }
})
