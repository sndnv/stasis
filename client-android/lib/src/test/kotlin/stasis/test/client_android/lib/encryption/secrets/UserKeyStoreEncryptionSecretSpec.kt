package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.UserKeyStoreEncryptionSecret

class UserKeyStoreEncryptionSecretSpec : WordSpec({
    "A UserKeyStoreEncryptionSecret" should {
        val encryptionIv = "6mSjkDoXUNNK8TGbebRYWXnjfskeVHXhaMxBRKD+ITvMckUp0ZQtUeEttz9pA0vWQ4MKa8otGmyD\nJ7OCrdeY4g=="
        val encryptionKey = "kROAgx70MxRKeDODRMyshRH0tswcd4jydKA60r+5knI="
        val secret = "BOunLSLKxVbluhDSPZ/wWw=="

        val keyStoreEncryptionSecret = UserKeyStoreEncryptionSecret(
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

        val encryptedDeviceSecret = "lEc6SelbtpNPtzcm6AjkXQR51FNeWuM0xkWSXKARiFQ=".decodeBase64()!!

        "encrypt device secrets" {
            val actualEncryptedSecret = keyStoreEncryptionSecret.encryptDeviceSecret(
                secret = deviceSecret
            )

            actualEncryptedSecret shouldBe (encryptedDeviceSecret)
        }

        "decrypt device secrets" {
            val actualDeviceSecret = keyStoreEncryptionSecret.decryptDeviceSecret(
                device = SecretsConfig.testDevice,
                encryptedSecret = encryptedDeviceSecret
            )

            actualDeviceSecret shouldBe (deviceSecret)
        }

        "not render its content via toString" {
            keyStoreEncryptionSecret.toString() shouldBe ("Secret(${keyStoreEncryptionSecret.javaClass.name})")
        }
    }
})
