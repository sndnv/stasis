package stasis.test.specs.unit.client.encryption.secrets

import stasis.client.encryption.secrets.{UserHashedAuthenticationPassword, UserHashedEncryptionPassword, UserPassword}
import stasis.test.specs.unit.UnitSpec
import stasis.test.specs.unit.client.EncodingHelpers

class UserPasswordSpec extends UnitSpec with SecretsConfig with EncodingHelpers {
  private val userPassword = UserPassword(user = testUser, salt = "some-user-salt", password = "some-user-password")

  "A UserPassword" should "support generating a hashed authentication password" in {
    val hashedPassword = "7iEgN9fcoXyR2epaaALDHoRdzcJzkvip1e16sInvsUrYm6+cJZi55Y23T86zDt1wQWx/ua42lHtV+VqBLl3ppg=="

    userPassword.toHashedAuthenticationPassword should be(
      UserHashedAuthenticationPassword(
        user = testUser,
        hashedPassword = hashedPassword.decodeFromBase64
      )
    )
  }

  it should "support generating a hashed encryption password" in {
    val hashedPassword = "GweSVUo/thLertEyWKS3y8N0THvkNJ9fFpdvjaLCnyz93ZHmZRgudJ4Wm5AZ8yF1xAFHZNtxsaYNXl5+o8RKzg=="

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
