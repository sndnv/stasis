package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import stasis.client_android.lib.encryption.secrets.UserHashedAuthenticationPassword
import stasis.client_android.lib.encryption.secrets.UserHashedEncryptionPassword
import stasis.client_android.lib.encryption.secrets.UserPassword

class UserPasswordSpec : WordSpec({
    "A UserPassword" should {
        val userPassword = UserPassword(
            user = SecretsConfig.testUser,
            salt = "some-user-salt",
            password = "some-user-password".toCharArray(),
            target = SecretsConfig.testConfig
        )

        "support generating a hashed authentication password" {
            val hashedPassword = "ssDIJULJGAzYLLHS7zPNteKz5jAEDb2Dmz8Ym/TZByR41BZ8nLol4OZlQvtkeAPG+CqB0hx56etnggKMKccH5Q=="

            userPassword.toHashedAuthenticationPassword() shouldBe (
                    UserHashedAuthenticationPassword(
                        user = SecretsConfig.testUser,
                        hashedPassword = hashedPassword.decodeBase64()!!
                    )
                    )
        }

        "support generating a hashed encryption password" {
            val hashedPassword = "IrTm/MALVpPlroD3yTH2gPdMEj1sT2G5oQ3zx6NGyBSqWzSc+2o0vkD0LhYtbP5V8PvJ6JiZWsDk8h7rWfS3zA=="

            userPassword.toHashedEncryptionPassword() shouldBe (
                    UserHashedEncryptionPassword(
                        user = SecretsConfig.testUser,
                        hashedPassword = hashedPassword.decodeBase64()!!,
                        target = SecretsConfig.testConfig
                    )
                    )
        }

        "not render its content via toString" {
            userPassword.toString() shouldBe ("Secret(${userPassword.javaClass.name})")
        }
    }
})
