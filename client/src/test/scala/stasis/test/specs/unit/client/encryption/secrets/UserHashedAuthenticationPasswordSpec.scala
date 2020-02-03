package stasis.test.specs.unit.client.encryption.secrets

import akka.util.ByteString
import stasis.client.encryption.secrets.UserHashedAuthenticationPassword
import stasis.shared.model.users.User
import stasis.test.specs.unit.UnitSpec

class UserHashedAuthenticationPasswordSpec extends UnitSpec {
  "A UserHashedAuthenticationPassword" should "allow extracting the hashed password" in {
    val expectedPassword = "test-password"
    val actualPassword = UserHashedAuthenticationPassword(
      user = User.generateId(),
      hashedPassword = ByteString(expectedPassword)
    )

    actualPassword.extract() should be(expectedPassword)
  }

  it should "fail if the password is extracted more than once" in {
    val expectedPassword = "test-password"
    val actualPassword = UserHashedAuthenticationPassword(
      user = User.generateId(),
      hashedPassword = ByteString(expectedPassword)
    )

    actualPassword.extract() should be(expectedPassword)

    an[IllegalStateException] should be thrownBy actualPassword.extract()
  }
}
