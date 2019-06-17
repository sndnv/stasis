package stasis.test.specs.unit.identity.model.secrets

import stasis.identity.model.secrets.Secret
import stasis.test.specs.unit.AsyncUnitSpec
import stasis.test.specs.unit.identity.EncodingHelpers
import stasis.test.specs.unit.identity.model.Generators

import scala.concurrent.duration._

class SecretSpec extends AsyncUnitSpec with EncodingHelpers {
  "A Secret" should "generate random password salt values" in {
    val salt = Secret.generateSalt()

    salt.length should be(testConfig.saltSize)
    salt.matches("^[A-Za-z0-9]+$") should be(true)
  }

  it should "derive hashed passwords from raw passwords" in {
    val rawPassword = "some-password"
    val salt = "some-salt"

    val expectedPassword = "yN/luUaC17kFf8KAwM4vtlFJtG+IL7/dxg6YpOyjB9g=".decodeFromBase64
    val actualPassword = Secret.derive(rawPassword, salt)

    actualPassword.value.size should be(testConfig.derivedKeySize)
    actualPassword.value should be(expectedPassword)
  }

  it should "allow comparing to other raw passwords" in {
    val rawPassword = "some-password"
    val salt = "some-salt"

    val hashedPassword = Secret.derive(rawPassword, salt)
    hashedPassword.isSameAs(rawPassword, salt) should be(true)
  }

  it should "not expose its value when converted to string" in {
    val secret = Generators.generateSecret(withSize = testConfig.derivedKeySize)
    secret.toString should be("Secret")
  }

  private implicit val testConfig: Secret.Config = Secret.ClientConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 3.seconds
  )
}
