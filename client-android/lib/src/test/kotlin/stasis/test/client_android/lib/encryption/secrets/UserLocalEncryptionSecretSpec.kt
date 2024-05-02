package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserLocalEncryptionSecret

class UserLocalEncryptionSecretSpec : WordSpec({
    "A UserLocalEncryptionSecret" should {
        val encryptionIv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
        val encryptionKey = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="
        val secret = "BOunLSLKxVbluhDSPZ/wWw=="

        val localEncryptionSecret = UserLocalEncryptionSecret(
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
            val actualEncryptedSecret = localEncryptionSecret.encryptDeviceSecret(
                secret = deviceSecret
            )

            actualEncryptedSecret shouldBe (encryptedDeviceSecret)
        }

        "decrypt device secrets" {
            val actualDeviceSecret = localEncryptionSecret.decryptDeviceSecret(
                device = SecretsConfig.testDevice,
                encryptedSecret = encryptedDeviceSecret
            )

            actualDeviceSecret shouldBe (deviceSecret)
        }

        "not render its content via toString" {
            localEncryptionSecret.toString() shouldBe ("Secret(${localEncryptionSecret.javaClass.name})")
        }
    }
})
