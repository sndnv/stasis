package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.encryption.secrets.DeviceFileSecret
import stasis.client_android.lib.encryption.secrets.DeviceMetadataSecret
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.model.core.CrateId
import java.math.BigInteger
import java.nio.file.Paths

class DeviceSecretSpec : WordSpec({
    "A DeviceMetadataSecret" should {
        val encryptionIv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
        val encryptionKey = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="
        val secret = "BOunLSLKxVbluhDSPZ/wWw=="

        val deviceSecret = DeviceSecret(
            user = SecretsConfig.testUser,
            device = SecretsConfig.testDevice,
            secret = secret.decodeBase64()!!,
            target = SecretsConfig.testConfig
        )

        val encryptedDeviceSecret = "z+pus9Glc/HssFVWozd+T2iuw4OOM4aeqcdDY+gvG8M=".decodeBase64()!!

        "support encryption" {
            val actualEncryptedSecret = deviceSecret.encrypted {
                Aes.encryption(
                    source = it,
                    key = encryptionKey.decodeBase64()!!,
                    iv = encryptionIv.decodeBase64()!!
                )
            }

            actualEncryptedSecret shouldBe (encryptedDeviceSecret)
        }

        "support decryption" {
            val actualDeviceSecret = DeviceSecret.decrypted(
                user = SecretsConfig.testUser,
                device = SecretsConfig.testDevice,
                encryptedSecret = encryptedDeviceSecret,
                decryptionStage = {
                    Aes.decryption(
                        source = it,
                        key = encryptionKey.decodeBase64()!!,
                        iv = encryptionIv.decodeBase64()!!
                    )
                },
                target = SecretsConfig.testConfig
            )

            actualDeviceSecret shouldBe (deviceSecret)
        }

        "support generating file secrets" {
            val file = Paths.get("/tmp/some/file")

            val iv = "uXE+Ru1aojwZa+8IVE49mg=="
            val key = "aHhX4zqPGYLnr+WI9RF23Q=="

            deviceSecret.toFileSecret(forFile = file, checksum = BigInteger.valueOf(42)) shouldBe (
                    DeviceFileSecret(
                        file = file,
                        iv = iv.decodeBase64()!!,
                        key = key.decodeBase64()!!
                    )
                    )
        }

        "support generating metadata secrets" {
            val metadata: CrateId = java.util.UUID.fromString("2b94caba-7c28-4322-9d72-fc8e72f884d5")

            val iv = "kUuYeWjrwqnA93zYCXn2ZC3Pr5Y4srYEcgrR3jP5KtM="
            val key = "QBqEu8Kh6iFGpbgYUWADXRfkVa6wUy5w"

            deviceSecret.toMetadataSecret(metadataCrate = metadata) shouldBe (
                    DeviceMetadataSecret(
                        iv = iv.decodeBase64()!!,
                        key = key.decodeBase64()!!
                    )
                    )
        }

        "not render its content via toString" {
            deviceSecret.toString() shouldBe ("Secret(${deviceSecret.javaClass.name})")
        }
    }
})
