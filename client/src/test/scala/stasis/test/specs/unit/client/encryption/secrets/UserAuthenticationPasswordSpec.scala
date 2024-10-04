package stasis.test.specs.unit.client.encryption.secrets

import org.apache.pekko.util.ByteString

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

  it should "support providing a digested version of the hashed password" in {
    val originalPassword = "test-password"
    val digestedPassword = "YrlMMruc2Em28w6fWV8YR9CYQYO4twcBhvWdczLS-9MjJchod8u4iUTbxVTpVOojGn3iCDXJpLHyNRXfR15kuQ"
    val actualPassword = UserAuthenticationPassword.Hashed(
      user = User.generateId(),
      hashedPassword = ByteString(originalPassword)
    )

    actualPassword.digested() should be(digestedPassword)
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

  it should "support providing a digested version of the raw password" in {
    val originalPassword = "test-password"
    val digestedPassword = "YrlMMruc2Em28w6fWV8YR9CYQYO4twcBhvWdczLS-9MjJchod8u4iUTbxVTpVOojGn3iCDXJpLHyNRXfR15kuQ"
    val actualPassword = UserAuthenticationPassword.Unhashed(
      user = User.generateId(),
      rawPassword = ByteString(originalPassword)
    )

    actualPassword.digested() should be(digestedPassword)
  }
}
