package stasis.test.specs.unit.shared.secrets

import com.typesafe.{config => typesafe}
import stasis.shared.secrets.SecretsConfig
import stasis.test.specs.unit.UnitSpec

class SecretsConfigSpec extends UnitSpec {
  "SecretsConfig" should "load its config" in {
    val expectedConfig = testConfig

    val actualConfig = SecretsConfig(
      config = config.getConfig("secrets"),
      ivSize = testIvSize
    )

    actualConfig should be(expectedConfig)
  }

  it should "validate its config" in {
    an[IllegalArgumentException] should be thrownBy testConfig.derivation.encryption.copy(secretSize = 8)
    an[IllegalArgumentException] should be thrownBy testConfig.derivation.encryption.copy(iterations = 95000)

    an[IllegalArgumentException] should be thrownBy testConfig.derivation.authentication.copy(secretSize = 12)
    an[IllegalArgumentException] should be thrownBy testConfig.derivation.authentication.copy(iterations = 99000)

    an[IllegalArgumentException] should be thrownBy testConfig.encryption.file.copy(keySize = 10)
    an[IllegalArgumentException] should be thrownBy testConfig.encryption.file.copy(ivSize = 6)

    an[IllegalArgumentException] should be thrownBy testConfig.encryption.metadata.copy(keySize = 12)
    an[IllegalArgumentException] should be thrownBy testConfig.encryption.metadata.copy(ivSize = 8)

    an[IllegalArgumentException] should be thrownBy testConfig.encryption.deviceSecret.copy(keySize = 14)
    an[IllegalArgumentException] should be thrownBy testConfig.encryption.deviceSecret.copy(ivSize = 10)
  }

  private val config: typesafe.Config = typesafe.ConfigFactory.load().getConfig("stasis.test.shared")

  private val testIvSize: Int = 12

  private val testConfig: SecretsConfig = SecretsConfig(
    derivation = SecretsConfig.Derivation(
      encryption = SecretsConfig.Derivation.Encryption(secretSize = 32, iterations = 100000, saltPrefix = "unit-test"),
      authentication = SecretsConfig.Derivation.Authentication(secretSize = 64, iterations = 150000, saltPrefix = "unit-test")
    ),
    encryption = SecretsConfig.Encryption(
      file = SecretsConfig.Encryption.File(keySize = 16, ivSize = testIvSize),
      metadata = SecretsConfig.Encryption.Metadata(keySize = 24, ivSize = testIvSize),
      deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 32, ivSize = testIvSize)
    )
  )
}
