package stasis.test.specs.unit.client.encryption.secrets

import stasis.client.encryption.secrets.Secret
import stasis.shared.model.devices.Device
import stasis.shared.model.users.User

trait SecretsConfig {
  protected val testUser: User.Id = java.util.UUID.fromString("731404fe-4e67-4767-b8d7-2814f126a5c8")
  protected val testDevice: Device.Id = java.util.UUID.fromString("434b9696-824c-4927-ba8e-dda5704084f8")

  protected implicit val testConfig: Secret.Config = Secret.Config(
    derivation = Secret.DerivationConfig(
      encryption = Secret.KeyDerivationConfig(secretSize = 64, iterations = 10000, saltPrefix = "unit-test"),
      authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 10000, saltPrefix = "unit-test")
    ),
    encryption = Secret.EncryptionConfig(
      file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
      metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
      deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
    )
  )
}
