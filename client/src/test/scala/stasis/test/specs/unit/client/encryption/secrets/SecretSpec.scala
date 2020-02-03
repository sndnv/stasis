package stasis.test.specs.unit.client.encryption.secrets

import com.typesafe.{config => typesafe}
import stasis.client.encryption.secrets.Secret
import stasis.test.specs.unit.UnitSpec

class SecretSpec extends UnitSpec {
  "Secrets" should "load config" in {
    val expectedConfig = testConfig

    val actualConfig = Secret.Config(
      rawConfig = config.getConfig("secrets"),
      ivSize = testIvSize
    )

    actualConfig should be(expectedConfig)
  }

  they should "validate their config" in {
    val derivationConfig = testConfig.derivation.authentication

    an[IllegalArgumentException] should be thrownBy derivationConfig.copy(secretSize = 8)
    an[IllegalArgumentException] should be thrownBy derivationConfig.copy(iterations = 10000)

    val encryptionConfig = testConfig.encryption.deviceSecret

    an[IllegalArgumentException] should be thrownBy encryptionConfig.copy(keySize = 8)
    an[IllegalArgumentException] should be thrownBy encryptionConfig.copy(ivSize = 8)
  }

  private val config: typesafe.Config = typesafe.ConfigFactory.load().getConfig("stasis.test.client")

  private val testIvSize: Int = 12

  private val testConfig: Secret.Config = Secret.Config(
    derivation = Secret.DerivationConfig(
      encryption = Secret.KeyDerivationConfig(secretSize = 32, iterations = 100000, saltPrefix = "unit-test"),
      authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 150000, saltPrefix = "unit-test")
    ),
    encryption = Secret.EncryptionConfig(
      file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = testIvSize),
      metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = testIvSize),
      deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = testIvSize)
    )
  )
}
