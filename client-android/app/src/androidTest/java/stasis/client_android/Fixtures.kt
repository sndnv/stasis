package stasis.client_android

import okio.ByteString.Companion.toByteString
import stasis.client_android.lib.encryption.secrets.DeviceSecret
import stasis.client_android.lib.encryption.secrets.Secret
import stasis.client_android.lib.encryption.secrets.UserPassword
import stasis.client_android.lib.model.EntityMetadata
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit
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

    object Metadata {
        val FileOneMetadata = EntityMetadata.File(
            path = Paths.get("/tmp/file/one"),
            size = 1,
            link = null,
            isHidden = false,
            created = Instant.MIN.truncatedTo(ChronoUnit.SECONDS),
            updated = Instant.MAX.truncatedTo(ChronoUnit.SECONDS),
            owner = "root",
            group = "root",
            permissions = "rwxrwxrwx",
            checksum = BigInteger("1"),
            crates = mapOf(
                Paths.get("/tmp/file/one_0") to UUID.fromString("329efbeb-80a3-42b8-b1dc-79bc0fea7bca")
            ),
            compression = "none"
        )
    }
}
