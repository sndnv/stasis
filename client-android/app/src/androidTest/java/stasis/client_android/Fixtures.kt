package stasis.client_android

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.encryption.secrets.UserPassword
import java.util.*

object Fixtures {
    object Secrets {
        val DefaultConfig = Secret.Config(
            derivation = Secret.Config.DerivationConfig(
                encryption = Secret.KeyDerivationConfig(
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = ""
                ),
                authentication = Secret.KeyDerivationConfig(
                    secretSize = 16,
                    iterations = 100000,
                    saltPrefix = ""
                )
            ),
            encryption = Secret.Config.EncryptionConfig(
                file = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 12),
                metadata = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 12),
                deviceSecret = Secret.EncryptionSecretConfig(keySize = 16, ivSize = 12)
            )
        )

        val Default = DeviceSecret(
            user = UUID.randomUUID(),
            device = UUID.randomUUID(),
            secret = "some-secret".toByteArray().toByteString(),
            target = DefaultConfig
        )

        val UserPassword = UserPassword(
            user = Default.user,
            salt = "test-salt",
            password = "some-password".toCharArray(),
            target = DefaultConfig
        )
    }
}
