package stasis.identity.model.secrets

import scala.concurrent.duration._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import stasis.identity.EncodingHelpers
import stasis.identity.model.Generators
import io.github.sndnv.layers.testing.UnitSpec

class SecretSpec extends UnitSpec with EncodingHelpers {
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

  it should "load client secrets config" in {
    val clientSecretsConfig = config.getConfig("secrets.client")

    val expectedConfig = Secret.ClientConfig(
      algorithm = "clients-some-algorithm",
      iterations = 1,
      derivedKeySize = 2,
      saltSize = 3,
      authenticationDelay = 4.millis
    )

    val actualConfig = Secret.ClientConfig(clientSecretsConfig)

    actualConfig should be(expectedConfig)
  }

  it should "load resource owner secrets config" in {
    val ownerSecretsConfig = config.getConfig("secrets.resource-owner")

    val expectedConfig = Secret.ResourceOwnerConfig(
      algorithm = "owners-some-algorithm",
      iterations = 5,
      derivedKeySize = 6,
      saltSize = 7,
      authenticationDelay = 8.millis
    )

    val actualConfig = Secret.ResourceOwnerConfig(ownerSecretsConfig)

    actualConfig should be(expectedConfig)
  }

  private val config: Config = ConfigFactory.load().getConfig("stasis.test.identity")

  private implicit val testConfig: Secret.Config = Secret.ClientConfig(
    algorithm = "PBKDF2WithHmacSHA512",
    iterations = 10000,
    derivedKeySize = 32,
    saltSize = 16,
    authenticationDelay = 3.seconds
  )
}
