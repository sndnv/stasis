package stasis.test.specs.unit.client.encryption.secrets

import stasis.client.encryption.secrets.{UserHashedAuthenticationPassword, UserHashedEncryptionPassword, UserPassword}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserPasswordSpec extends UnitSpec with SecretsConfig with EncodingHelpers {
  private val userPassword = UserPassword(user = testUser, salt = "some-user-salt", password = "some-user-password")

  "A UserPassword" should "support generating a hashed authentication password" in {
    val hashedPassword = "ssDIJULJGAzYLLHS7zPNteKz5jAEDb2Dmz8Ym/TZByR41BZ8nLol4OZlQvtkeAPG+CqB0hx56etnggKMKccH5Q=="

    userPassword.toHashedAuthenticationPassword should be(
      UserHashedAuthenticationPassword(
        user = testUser,
        hashedPassword = hashedPassword.decodeFromBase64
      )
    )
  }

  it should "support generating a hashed encryption password" in {
    val hashedPassword = "IrTm/MALVpPlroD3yTH2gPdMEj1sT2G5oQ3zx6NGyBSqWzSc+2o0vkD0LhYtbP5V8PvJ6JiZWsDk8h7rWfS3zA=="

    userPassword.toHashedEncryptionPassword should be(
      UserHashedEncryptionPassword(
        user = testUser,
        hashedPassword = hashedPassword.decodeFromBase64
      )
    )
  }

  it should "not render its content via toString" in {
    userPassword.toString should be(s"Secret(${userPassword.getClass.getName})")
  }
}
