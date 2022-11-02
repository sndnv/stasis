package stasis.test.specs.unit.client.encryption.secrets

import akka.util.ByteString
import stasis.client.encryption.secrets.UserAuthenticationPassword
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class UserAuthenticationPasswordSpec extends UnitSpec {
  "A Hashed UserAuthenticationPassword" should "allow extracting the hashed password" in {
    val originalPassword = "test-password"
    val expectedPassword = "dGVzdC1wYXNzd29yZA"
    val actualPassword = UserAuthenticationPassword.Hashed(
      user = User.generateId(),
      hashedPassword = ByteString(originalPassword)
    )

    actualPassword.extract() should be(expectedPassword)
  }

  it should "fail if the password is extracted more than once" in {
    val originalPassword = "test-password"
    val expectedPassword = "dGVzdC1wYXNzd29yZA"
    val actualPassword = UserAuthenticationPassword.Hashed(
      user = User.generateId(),
      hashedPassword = ByteString(originalPassword)
    )

    actualPassword.extract() should be(expectedPassword)

    an[IllegalStateException] should be thrownBy actualPassword.extract()
  }

  "An Unhashed UserAuthenticationPassword" should "allow extracting the raw password" in {
    val originalPassword = "test-password"
    val actualPassword = UserAuthenticationPassword.Unhashed(
      user = User.generateId(),
      rawPassword = ByteString(originalPassword)
    )

    actualPassword.extract() should be(originalPassword)
  }

  it should "fail if the password is extracted more than once" in {
    val originalPassword = "test-password"
    val actualPassword = UserAuthenticationPassword.Unhashed(
      user = User.generateId(),
      rawPassword = ByteString(originalPassword)
    )

    actualPassword.extract() should be(originalPassword)

    an[IllegalStateException] should be thrownBy actualPassword.extract()
  }
}
