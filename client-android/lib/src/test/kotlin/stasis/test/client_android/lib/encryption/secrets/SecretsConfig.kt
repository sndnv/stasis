package stasis.test.client_android.lib.encryption.secrets

import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.model.server.devices.DeviceId
import stasis.client_android.lib.model.server.users.UserId

object SecretsConfig {
    val testUser: UserId = java.util.UUID.fromString("731404fe-4e67-4767-b8d7-2814f126a5c8")
    val testDevice: DeviceId = java.util.UUID.fromString("434b9696-824c-4927-ba8e-dda5704084f8")

    val testConfig: Secret.Config = Secret.Config(
        derivation = Secret.Config.DerivationConfig(
            encryption = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test"),
            authentication = Secret.KeyDerivationConfig(secretSize = 64, iterations = 100000, saltPrefix = "unit-test")
        ),
        encryption = Secret.Config.EncryptionConfig(
            file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 16),
            metadata = Secret.EncryptionSecretConfig(keySize = 24, ivSize = 32),
            deviceSecret = Secret.EncryptionSecretConfig(keySize = 32, ivSize = 64)
        )
    )
}
