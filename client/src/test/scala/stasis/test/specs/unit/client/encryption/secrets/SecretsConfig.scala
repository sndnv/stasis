package stasis.test.specs.unit.client.encryption.secrets

import stasis.shared.model.devices.Device
import stasis.shared.model.users.User
import stasis.shared.secrets.{SecretsConfig => Secrets}

trait SecretsConfig {
  protected val testUser: User.Id = java.util.UUID.fromString("731404fe-4e67-4767-b8d7-2814f126a5c8")
  protected val testDevice: Device.Id = java.util.UUID.fromString("434b9696-824c-4927-ba8e-dda5704084f8")

  protected implicit val testConfig: Secrets = Secrets(
    derivation = Secrets.Derivation(
      encryption = Secrets.Derivation.Encryption(secretSize = 64, iterations = 100000, saltPrefix = "unit-test"),
      authentication = Secrets.Derivation.Authentication(secretSize = 64, iterations = 100000, saltPrefix = "unit-test")
    ),
    encryption = Secrets.Encryption(
      file = Secrets.Encryption.File(keySize = 16, ivSize = 16),
      metadata = Secrets.Encryption.Metadata(keySize = 24, ivSize = 32),
      deviceSecret = Secrets.Encryption.DeviceSecret(keySize = 32, ivSize = 64)
    )
  )
}
