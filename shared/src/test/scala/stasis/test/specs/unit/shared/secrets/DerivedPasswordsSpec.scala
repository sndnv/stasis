package stasis.test.specs.unit.shared.secrets

import org.apache.pekko.util.ByteString
import stasis.shared.secrets.DerivedPasswords
import stasis.test.specs.unit.UnitSpec

class DerivedPasswordsSpec extends UnitSpec {
  "DerivedPasswords" should "support deriving hashed authentication passwords" in {
    val expected = "1owhNJEee+Av51YxBXblvBoTOyHD7KN9"

    val actual = DerivedPasswords
      .deriveHashedAuthenticationPassword(
        password = "test-password".toCharArray,
        saltPrefix = "test-prefix",
        salt = "test-salt",
        iterations = 100000,
        derivedKeySize = 24
      )
      .encodeBase64
      .utf8String

    actual should be(expected)
  }

  they should "support deriving hashed encryption passwords" in {
    val expected = "BdqLs770SPOE1QXcB2zJ/qMbdg4+/wdUuilGKeWGwSk="

    val actual = DerivedPasswords
      .deriveHashedEncryptionPassword(
        password = "test-password".toCharArray,
        saltPrefix = "test-prefix",
        salt = "test-salt",
        iterations = 100000,
        derivedKeySize = 32
      )
      .encodeBase64
      .utf8String

    actual should be(expected)
  }

  they should "support encoding hashed passwords" in {
    val original = ByteString("some-hashed-string")
    val expected = "c29tZS1oYXNoZWQtc3RyaW5n"
    val actual = DerivedPasswords.encode(hashedPassword = original)

    actual should be(expected)
  }
}
