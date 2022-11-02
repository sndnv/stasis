package stasis.test.client_android.lib.encryption.secrets

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8
import stasis.client_android.lib.encryption.secrets.UserAuthenticationPassword
import java.util.UUID

class UserAuthenticationPasswordSpec : WordSpec({
    "A Hashed UserAuthenticationPassword" should {
        "allow extracting the hashed password" {
            val originalPassword = "test-password"
            val expectedPassword = "dGVzdC1wYXNzd29yZA"

            val actualPassword = UserAuthenticationPassword.Hashed(
                user = UUID.randomUUID(),
                hashedPassword = originalPassword.encodeUtf8()
            )

            actualPassword.extract() shouldBe (expectedPassword)
        }

        "fail if the password is extracted more than once" {
            val originalPassword = "test-password"
            val expectedPassword = "dGVzdC1wYXNzd29yZA"

            val actualPassword = UserAuthenticationPassword.Hashed(
                user = UUID.randomUUID(),
                hashedPassword = originalPassword.encodeUtf8()
            )

            actualPassword.extract() shouldBe (expectedPassword)

            shouldThrow<IllegalStateException> {
                actualPassword.extract()
            }
        }
    }

    "A Unhashed UserAuthenticationPassword" should {
        "allow extracting the raw password" {
            val originalPassword = "test-password"

            val actualPassword = UserAuthenticationPassword.Unhashed(
                user = UUID.randomUUID(),
                rawPassword = originalPassword.encodeUtf8()
            )

            actualPassword.extract() shouldBe (originalPassword)
        }

        "fail if the password is extracted more than once" {
            val originalPassword = "test-password"

            val actualPassword = UserAuthenticationPassword.Unhashed(
                user = UUID.randomUUID(),
                rawPassword = originalPassword.encodeUtf8()
            )

            actualPassword.extract() shouldBe (originalPassword)

            shouldThrow<IllegalStateException> {
                actualPassword.extract()
            }
        }
    }
})
