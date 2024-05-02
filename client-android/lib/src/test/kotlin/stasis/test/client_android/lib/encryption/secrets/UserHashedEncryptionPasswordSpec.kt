package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import stasis.client_android.lib.encryption.secrets.UserLocalEncryptionSecret
import stasis.client_android.lib.encryption.secrets.UserHashedEncryptionPassword
import stasis.client_android.lib.encryption.secrets.UserKeyStoreEncryptionSecret

class UserHashedEncryptionPasswordSpec : WordSpec({
    "A UserHashedEncryptionPassword" should {
        val hashedPassword =
            "US794fdkdF/LvnVnSo4LjD78QhaT0iUTaQA1u78vz+z6MGagfoHbcFpgtdhTDz2IM5zTL0LFOh0cZmghGfu8jQ=="

        val encryptionPassword = UserHashedEncryptionPassword(
            user = SecretsConfig.testUser,
            hashedPassword = hashedPassword.decodeBase64()!!,
            target = SecretsConfig.testConfig
        )

        "support generating local encryption secrets" {
            val iv = "J9vRvveXTnC0iF4ymYbIo5racLWx60CGxcOlklH/qH4xqIKvlsZQyr66bGFxzpYrayRS7iipCVimlYt7BCj7uQ=="
            val key = "nXT1Bw0YCrk79xgnvlUJ5CZByYD9nuSZo9XQghf1xQU="

            encryptionPassword.toLocalEncryptionSecret() shouldBe (
                    UserLocalEncryptionSecret(
                        user = SecretsConfig.testUser,
                        iv = iv.decodeBase64()!!,
                        key = key.decodeBase64()!!,
                        target = SecretsConfig.testConfig
                    )
                    )
        }

        "support generating keys-tore encryption secrets" {
            val iv = "6mSjkDoXUNNK8TGbebRYWXnjfskeVHXhaMxBRKD+ITvMckUp0ZQtUeEttz9pA0vWQ4MKa8otGmyD\nJ7OCrdeY4g=="
            val key = "kROAgx70MxRKeDODRMyshRH0tswcd4jydKA60r+5knI="

            encryptionPassword.toKeyStoreEncryptionSecret() shouldBe (
                    UserKeyStoreEncryptionSecret(
                        user = SecretsConfig.testUser,
                        iv = iv.decodeBase64()!!,
                        key = key.decodeBase64()!!,
                        target = SecretsConfig.testConfig
                    )
                    )
        }

        "not render its content via toString" {
            encryptionPassword.toString() shouldBe ("Secret(${encryptionPassword.javaClass.name})")
        }
    }
})
