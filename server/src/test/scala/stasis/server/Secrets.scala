package stasis.server

import stasis.shared.secrets.SecretsConfig

trait Secrets {
  protected implicit val testSecretsConfig: SecretsConfig = SecretsConfig(
    derivation = SecretsConfig.Derivation(
      encryption = SecretsConfig.Derivation.Encryption(
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      ),
      authentication = SecretsConfig.Derivation.Authentication(
        enabled = true,
        secretSize = 64,
        iterations = 100000,
        saltPrefix = "unit-test"
      )
    ),
    encryption = SecretsConfig.Encryption(
      file = SecretsConfig.Encryption.File(keySize = 16, ivSize = 16),
      metadata = SecretsConfig.Encryption.Metadata(keySize = 24, ivSize = 32),
      deviceSecret = SecretsConfig.Encryption.DeviceSecret(keySize = 32, ivSize = 64)
    )
  )
}
