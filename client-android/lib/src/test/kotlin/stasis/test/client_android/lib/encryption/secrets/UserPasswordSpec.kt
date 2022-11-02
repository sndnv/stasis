package stasis.test.client_android.lib.encryption.secrets

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import stasis.client_android.lib.encryption.secrets.UserHashedEncryptionPassword
import stasis.client_android.lib.encryption.secrets.UserPassword

class UserPasswordSpec : WordSpec({
    "A UserPassword" should {
        "support generating a hashed authentication password" {
            val userPassword = UserPassword(
                user = SecretsConfig.testUser,
                salt = "some-user-salt",
                password = "some-user-password".toCharArray(),
                target = SecretsConfig.testConfig.copy(
                    derivation = SecretsConfig.testConfig.derivation.copy(
                        authentication = SecretsConfig.testConfig.derivation.authentication.copy(enabled = true)
                    )
                )
            )

            val hashedPassword = "ssDIJULJGAzYLLHS7zPNteKz5jAEDb2Dmz8Ym/TZByR41BZ8nLol4OZlQvtkeAPG+CqB0hx56etnggKMKccH5Q=="

            userPassword.toAuthenticationPassword() shouldBe (
                    UserAuthenticationPassword.Hashed(
                        user = SecretsConfig.testUser,
                        hashedPassword = hashedPassword.decodeBase64()!!
                    )
                    )
        }

        "support generating an unhashed authentication password" {
            val originalPassword = "some-user-password"

            val userPassword = UserPassword(
                user = SecretsConfig.testUser,
                salt = "some-user-salt",
                password = originalPassword.toCharArray(),
                target = SecretsConfig.testConfig.copy(
                    derivation = SecretsConfig.testConfig.derivation.copy(
                        authentication = SecretsConfig.testConfig.derivation.authentication.copy(enabled = false)
                    )
                )
            )

            userPassword.toAuthenticationPassword() shouldBe (
                    UserAuthenticationPassword.Unhashed(
                        user = SecretsConfig.testUser,
                        rawPassword = originalPassword.toByteArray().toByteString()
                    )
                    )
        }

        "support generating a hashed encryption password" {
            val userPassword = UserPassword(
                user = SecretsConfig.testUser,
                salt = "some-user-salt",
                password = "some-user-password".toCharArray(),
                target = SecretsConfig.testConfig
            )

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
            val userPassword = UserPassword(
                user = SecretsConfig.testUser,
                salt = "some-user-salt",
                password = "some-user-password".toCharArray(),
                target = SecretsConfig.testConfig
            )

            userPassword.toString() shouldBe ("Secret(${userPassword.javaClass.name})")
        }
    }
})
