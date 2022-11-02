package stasis.test.specs.unit.client.encryption.secrets

import akka.util.ByteString
import stasis.client.encryption.secrets.{UserAuthenticationPassword, UserHashedEncryptionPassword, UserPassword}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserPasswordSpec extends UnitSpec with SecretsConfig with EncodingHelpers {
  "A UserPassword" should "support generating a hashed authentication password" in {
    val secretsConfig = testConfig.copy(derivation =
      testConfig.derivation.copy(authentication = testConfig.derivation.authentication.copy(enabled = true))
    )

    val originalPassword = "some-user-password"
    val hashedPassword = "ssDIJULJGAzYLLHS7zPNteKz5jAEDb2Dmz8Ym/TZByR41BZ8nLol4OZlQvtkeAPG+CqB0hx56etnggKMKccH5Q=="

    val userPassword = UserPassword(
      user = testUser,
      salt = "some-user-salt",
      password = originalPassword.toCharArray
    )(target = secretsConfig)

    userPassword.toAuthenticationPassword should be(
      UserAuthenticationPassword.Hashed(
        user = testUser,
        hashedPassword = hashedPassword.decodeFromBase64
      )
    )
  }

  it should "support generating an unhashed authentication password" in {
    val secretsConfig = testConfig.copy(derivation =
      testConfig.derivation.copy(authentication = testConfig.derivation.authentication.copy(enabled = false))
    )

    val originalPassword = "some-user-password"

    val userPassword = UserPassword(
      user = testUser,
      salt = "some-user-salt",
      password = originalPassword.toCharArray
    )(target = secretsConfig)

    userPassword.toAuthenticationPassword should be(
      UserAuthenticationPassword.Unhashed(
        user = testUser,
        rawPassword = ByteString(originalPassword)
      )
    )
  }

  it should "support generating a hashed encryption password" in {
    val userPassword = UserPassword(
      user = testUser,
      salt = "some-user-salt",
      password = "some-user-password".toCharArray
    )

    val hashedPassword = "IrTm/MALVpPlroD3yTH2gPdMEj1sT2G5oQ3zx6NGyBSqWzSc+2o0vkD0LhYtbP5V8PvJ6JiZWsDk8h7rWfS3zA=="

    userPassword.toHashedEncryptionPassword should be(
      UserHashedEncryptionPassword(
        user = testUser,
        hashedPassword = hashedPassword.decodeFromBase64
      )
    )
  }

  it should "not render its content via toString" in {
    val userPassword = UserPassword(
      user = testUser,
      salt = "some-user-salt",
      password = "some-user-password".toCharArray
    )

    userPassword.toString should be(s"Secret(${userPassword.getClass.getName})")
  }
}
