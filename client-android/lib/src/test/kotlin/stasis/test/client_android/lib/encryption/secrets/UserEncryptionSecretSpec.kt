package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserEncryptionSecret

class UserEncryptionSecretSpec : WordSpec({
    "A UserEncryptionSecret" should {
        val encryptionIv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
        val encryptionKey = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="
        val secret = "BOunLSLKxVbluhDSPZ/wWw=="

        val encryptionSecret = UserEncryptionSecret(
            user = SecretsConfig.testUser,
            iv = encryptionIv.decodeBase64()!!,
            key = encryptionKey.decodeBase64()!!,
            target = SecretsConfig.testConfig
        )

        val deviceSecret = DeviceSecret(
            user = SecretsConfig.testUser,
            device = SecretsConfig.testDevice,
            secret = secret.decodeBase64()!!,
            target = SecretsConfig.testConfig
        )

        val encryptedDeviceSecret = "z+pus9Glc/HssFVWozd+T2iuw4OOM4aeqcdDY+gvG8M=".decodeBase64()!!

        "encrypt device secrets" {
            val actualEncryptedSecret = encryptionSecret.encryptDeviceSecret(
                secret = deviceSecret
            )

            actualEncryptedSecret shouldBe (encryptedDeviceSecret)
        }

        "decrypt device secrets" {
            val actualDeviceSecret = encryptionSecret.decryptDeviceSecret(
                device = SecretsConfig.testDevice,
                encryptedSecret = encryptedDeviceSecret
            )

            actualDeviceSecret shouldBe (deviceSecret)
        }

        "not render its content via toString" {
            encryptionSecret.toString() shouldBe ("Secret(${encryptionSecret.javaClass.name})")
        }
    }
})
